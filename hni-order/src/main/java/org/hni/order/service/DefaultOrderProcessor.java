package org.hni.order.service;

import org.hni.common.exception.HNIException;
import org.hni.events.service.EventRouter;
import org.hni.events.service.om.Event;
import org.hni.events.service.om.EventName;
import org.hni.order.dao.DefaultPartialOrderDAO;
import org.hni.order.om.Order;
import org.hni.order.om.OrderItem;
import org.hni.order.om.PartialOrder;
import org.hni.order.om.TransactionPhase;
import org.hni.order.om.type.OrderStatus;
import org.hni.provider.om.GeoCodingException;
import org.hni.provider.om.Menu;
import org.hni.provider.om.MenuItem;
import org.hni.provider.om.ProviderLocation;
import org.hni.provider.service.ProviderLocationService;
import org.hni.user.dao.UserDAO;
import org.hni.user.om.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DefaultOrderProcessor implements OrderProcessor {

    private static Logger logger = LoggerFactory.getLogger(DefaultOrderProcessor.class);

    @Inject
    private UserDAO userDao;

    @Inject
    private DefaultPartialOrderDAO partialOrderDAO;

    @Inject
    private ProviderLocationService locationService;

    @Inject
    private OrderService orderService;

    @Inject
    private EventRouter eventRouter;

    @PostConstruct
    void init() {
        if (eventRouter.getRegistered(EventName.MEAL) != this) {
            eventRouter.registerService(EventName.MEAL, this);
        }
    }

    public String processMessage(User user, String message) {
        PartialOrder order = partialOrderDAO.byUser(user);
        boolean cancellation = message.equalsIgnoreCase("ENDMEAL");

        if (order == null && cancellation) {
            return "You are not currently ordering, please respond with MEAL to place an order.";
        } else if (order == null && !message.equalsIgnoreCase("STATUS")) {
            order = new PartialOrder();
            order.setTransactionPhase(TransactionPhase.MEAL);
            order.setUser(user);
        } else if (order == null) {
            //status check
            return checkOrderStatus(user);
        } else if (cancellation) {
            partialOrderDAO.delete(order);
            return "You have successfully cancelled your order.";
        }

        TransactionPhase phase = order.getTransactionPhase();
        String output = "";

        switch (phase) {
            case MEAL:
                output = requestingMeal(message, order);
                break;
            case PROVIDING_ADDRESS:
                output = findNearbyMeals(message, order);
                break;
            case CHOOSING_LOCATION:
                output = chooseLocation(message, order);
                break;
            case CHOOSING_MENU_ITEM:
                //this is chosen w/ provider for now
                break;
            case CONFIRM_OR_REDO:
                return confirmOrContinueOrder(message, order);
            default:
                //shouldn't get here
        }
        partialOrderDAO.save(order);
        return output;
    }

    public String processMessage(Long userId, String message) {
        return processMessage(userDao.get(userId), message);
    }

    private String requestingMeal(String request, PartialOrder order) {
        order.setTransactionPhase(TransactionPhase.PROVIDING_ADDRESS);
        if (request.equalsIgnoreCase("MEAL") || request.equalsIgnoreCase("ORDER")) {
            return "Please provide your address or ENDMEAL to quit";
        } else {
            return "I don't understand that, please say MEAL to request a meal.";
        }
    }

    private String findNearbyMeals(String addressString, PartialOrder order) {
        String output = "";
        try {
            List<ProviderLocation> nearbyProviders = (ArrayList) locationService.providersNearCustomer(addressString, 3);
            if (!nearbyProviders.isEmpty()) {
                order.setAddress(addressString);
                List<ProviderLocation> nearbyWithMenu = new ArrayList<>();
                List<MenuItem> items = new ArrayList<>();
                for (ProviderLocation location : nearbyProviders) {
                    Optional<Menu> currentMenu = location.getProvider().getMenus().stream()
                            .filter(menu -> isCurrent(menu)).findFirst();
                    if (currentMenu.isPresent()) {
                        nearbyWithMenu.add(location);
                        items.add(currentMenu.get().getMenuItems().iterator().next());
                    }
                }
                if (!nearbyWithMenu.isEmpty()) {
                    order.setProviderLocationsForSelection(nearbyWithMenu);
                    order.setMenuItemsForSelection(items);
                    output += providerLocationMenuOutput(order);
                    order.setTransactionPhase(TransactionPhase.CHOOSING_LOCATION);
                } else {
                    output = "All providers there are not currently available. Please try again later, provide a different address or reply ENDMEAL to quit";
                }
            } else {
                output = "No provider locations near this address. Please provide another address or ENDMEAL to quit";
            }
        } catch (GeoCodingException e) {
            output = e.getMessage();
        }

        return output;
    }


    private String chooseLocation(String message, PartialOrder order) {
        String output = "";
        try {
            int index = Integer.parseInt(message);
            if (index < 1 || index > 3) {
                throw new IndexOutOfBoundsException();
            }
            ProviderLocation location = order.getProviderLocationsForSelection().get(index - 1);
            order.setChosenProvider(location);
            MenuItem chosenItem = order.getMenuItemsForSelection().get(index - 1);
            order.getMenuItemsSelected().add(chosenItem);
            logger.debug("Location {} has been chosen with item {}", location.getName(), chosenItem.getName());
            order.setTransactionPhase(TransactionPhase.CONFIRM_OR_REDO);
            output = String.format("You have chosen %s at %s. Respond with CONFIRM to place this order, REDO to try again, or ENDMEAL to end your order", chosenItem.getName(), location.getName());
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            output += "Invalid input! ";
            output += providerLocationMenuOutput(order);
        }
        return output;
    }

    private String confirmOrContinueOrder(String message, PartialOrder order) {
        String output = "";
        switch (message.toUpperCase()) {
            case "CONFIRM":
                //create a final order and set initial info
                Order finalOrder = new Order();
                finalOrder.setUserId(order.getUser().getId());
                finalOrder.setOrderDate(new Date());
                finalOrder.setProviderLocation(order.getChosenProvider());
                //set items being ordered
                Set<MenuItem> chosenItems = order.getMenuItemsSelected();
                Set<OrderItem> orderedItems = chosenItems.stream().map(mItem -> new OrderItem(1L, mItem.getPrice(), mItem))
                        .collect(Collectors.toSet());
                orderedItems.forEach(item -> item.setOrder(finalOrder));
                finalOrder.setOrderItems(orderedItems);
                finalOrder.setSubTotal(orderedItems.stream().map(item -> (item.getAmount() * item.getQuantity())).reduce(0.0, Double::sum));
                finalOrder.setStatus(OrderStatus.OPEN);
                orderService.save(finalOrder);
                partialOrderDAO.delete(order);
                logger.info("Successfully created order {}", finalOrder.getId());
                output = "Your order has been confirmed, please respond with STATUS after 5 minutes to check to status of your order.";
                break;
            case "REDO":
                logger.info("Reset order choices for PartialOrder {} by user request", order.getId());
                //clear out previous choices
                output = findNearbyMeals(order.getAddress(), order);
                //save here because I know caller won't
                partialOrderDAO.save(order);
                break;
            default:
                output += "Please respond with CONFIRM, REDO, or ENDMEAL";
        }
        return output;
    }

    private String checkOrderStatus(User user) {
        Collection<Order> orders = orderService.get(user, LocalDate.now());
        Optional<Order> order = orders.stream().sorted((a,b) -> b.getOrderDate().compareTo(a.getOrderDate())).findFirst();
        if (order.isPresent()) {
            OrderStatus status = order.get().getOrderStatus();
            if (status.equals(OrderStatus.OPEN)) {
                return "Your order is still open, please respond with STATUS in 5 minutes to check again.";
            } else if (status.equals(OrderStatus.ORDERED)) {
                return "Your order has been placed and should be ready to pick up shortly from " + order.get().getProviderLocation().getAddress().getAddress1();
            } else {
                //TODO should we say anything for if they suspect an error
                return "Your order has been marked as closed";
            }
        } else {
            return "I can not find a recent order for you, please respond with MEAL to place an order.";
        }
    }

    /**
     * This method loops through the providerLocations of an order to create a string output of them the menu items
     * they contain.
     *
     * @param order
     * @return
     */
    private String providerLocationMenuOutput(PartialOrder order) {
        String output = "";
        output += "Please provide the number for your selection.";
        for (int i = 0; i < order.getProviderLocationsForSelection().size(); i ++) {
            ProviderLocation location = order.getProviderLocationsForSelection().get(i);
            output += " " + (i + 1) + ") " + location.getName()
                    + " (" + order.getMenuItemsForSelection().get(i).getName() + ") "
                    + location.getAddress().getAddress1();
            String line2 = location.getAddress().getAddress2();
            if (line2 != null && !line2.isEmpty()) {
                output += " " + line2;
            }
            output += ".";
        }
        return output;
    }

    private boolean isCurrent(Menu menu) {
        //TODO this has issues between 11:00 and 11:59 pm because minutes are not stored in db
        LocalTime now = LocalTime.now();
        LocalTime start = LocalTime.of(menu.getStartHourAvailable().intValue(), 0);
        LocalTime end = LocalTime.of(menu.getEndHourAvailable().intValue(), 0);

        if (start.compareTo(end) < 0) {
            //eg start at 06:00 and end at 12:00
            return start.compareTo(now) < 0 && now.compareTo(end) < 0;
        } else {
            //eg start at 11:00 end at 04:00
            return (start.compareTo(now) < 0 && now.compareTo(LocalTime.MAX) < 0) ||
                    (now.compareTo(LocalTime.MIN) > 0 && now.compareTo(end) < 0);
        }
    }

    @Override
    public String handleEvent(Event event) {

        // Look up the user
        String phoneNumber = event.getPhoneNumber();
        List<User> users = userDao.byMobilePhone(phoneNumber);

        if (users != null && !users.isEmpty()) {
            // process the text message
            return processMessage(users.get(0), event.getTextMessage().trim());
        } else {
            String message = "OrderProcessor failed to lookup user by phone " + phoneNumber;
            logger.error(message);
            throw new HNIException("Please sign up first by saying REGISTER");
        }
    }
}