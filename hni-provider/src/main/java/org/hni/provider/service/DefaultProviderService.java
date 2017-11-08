package org.hni.provider.service;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.hni.common.service.AbstractService;
import org.hni.organization.om.UserOrganizationRole;
import org.hni.organization.service.OrganizationUserService;
import org.hni.provider.dao.ProviderDAO;
import org.hni.provider.om.Provider;
import org.hni.provider.om.ProviderLocation;
import org.hni.type.HNIRoles;
import org.hni.user.om.User;
import org.hni.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
public class DefaultProviderService extends AbstractService<Provider> implements ProviderService {
	private static final Logger _LOGGER = LoggerFactory.getLogger(DefaultProviderService.class);
	
	private ProviderDAO providerDao;
	
	@Inject
	private OrganizationUserService organizationUserService;
	
	@Inject
	private ProviderLocationService providerLocationService;
	
	@Inject
	public DefaultProviderService(ProviderDAO providerDao) {
		super(providerDao);
		this.providerDao = providerDao;
	}

	@Override
	public Provider getProviderDetails(Long providerId, User loggedInUser) {
		_LOGGER.debug("Starting process for retrieve provider");
		Provider toProvider = get(providerId);
		if (isAllowed(loggedInUser, toProvider)) {
			return toProvider;
		}
		return null;
	}
	
	private boolean isAllowed(User loggedInUser, Provider toProvider){
		boolean isAllowed = false;
		if(toProvider.getCreatedById().equals(loggedInUser.getId())){
			_LOGGER.debug("Access granted for created user {}", loggedInUser.getId());
			isAllowed =  true;
		}else if(isAdminRole(loggedInUser)){
			_LOGGER.debug("Access granted for logged in user {}", loggedInUser.getId());
			isAllowed =  true;
		}else{
			_LOGGER.debug("Unauthorized access , logged in user {}", loggedInUser.getId());
			isAllowed =  false;
		}
		
		return isAllowed;
	}
	
	private boolean isAdminRole(User loggedInUser){
		boolean isAdminRole = false;
		List<UserOrganizationRole> performerRoles = (List<UserOrganizationRole>) organizationUserService
				.getUserOrganizationRoles(loggedInUser);

		if (!performerRoles.isEmpty()) {
			UserOrganizationRole performerRole = performerRoles.get(0);
			Long performerRoleId = performerRole.getId().getRoleId();
			isAdminRole = checkPermission(performerRoleId);
		}
		return isAdminRole;
	}
	
	private boolean checkPermission(Long performerRoleId){
		if (performerRoleId.equals(HNIRoles.CLIENT.getRole())) {
			// No action allowed for a client/participant
			return false;
		} else if (performerRoleId.equals(HNIRoles.SUPER_ADMIN.getRole())) {
			return true;
		} 
		return false;
	}

	@Override
	public List<ProviderLocation> getProviderLocations(Long providerId,
			User loggedInUser) {
		_LOGGER.debug("Starting process for retrieve provider locations");
		Provider toProvider = get(providerId);

		if (isAllowed(loggedInUser, toProvider)) {
			return providerLocationService.locationsOf(providerId);
		}
		return null;
	}

}
