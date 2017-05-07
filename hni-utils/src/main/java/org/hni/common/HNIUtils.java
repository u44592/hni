package org.hni.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HNIUtils {
	public static String getUUID() {
		return UUID.randomUUID().toString().replaceAll("-", "").trim();
	}

	public static List<Map<String, String>> getReportHeaders(Integer reportId) {

		List<Map<String, String>> headers = new ArrayList<>();
		if (Constants.REPORT_ALL_NGO.equals(reportId)) {

			headers.add(addField("name", "NGO Name"));
			headers.add(addField("phone", "NGO Phone"));
			headers.add(addField("address", "NGO Address"));
			headers.add(addField("createdUsers", "Total number of Clients "));
		} else if (Constants.REPORT_ALL_CUSTOMER.equals(reportId)) {

			headers.add(addField("firstName", "First Name"));
			headers.add(addField("lastName", "Last Name"));
			headers.add(addField("mobilePhone", "Phone Number"));
			headers.add(addField("race", "Race"));
			headers.add(addField("address", "Address"));
			headers.add(addField("orders", "No of Orders"));

		} else if(Constants.REPORT_ALL_VOLUNTEER.equals(reportId)){
			headers.add(addField("firstName", "First Name"));
			headers.add(addField("lastName", "Last Name"));
			headers.add(addField("address", "Address"));
			headers.add(addField("phone", "Phone Number"));
			headers.add(addField("email", "Email"));
		}
		else if(Constants.REPORT_ALL_ORDER.equals(reportId)){
			headers.add(addField("orderDate", "Order date"));
			headers.add(addField("readyDate", "Ready date"));
			headers.add(addField("name", "Orderd By"));
			headers.add(addField("orderstatus", "Order status"));
			headers.add(addField("total", "Total"));
			//headers.add(addField("orderItems", "Ordered Items"));
		}
		else if (Constants.REPORT_ALL_PROVIDER.equals(reportId)) {

			headers.add(addField("name", "Provider Name"));
			headers.add(addField("address", "Address"));
			headers.add(addField("website", "Website"));
			headers.add(addField("createdOn", "Created On"));
			headers.add(addField("createdBy", "Created By"));	
			}
		return headers;
	}

	private static Map<String, String> addField(String field, String label) {
		Map<String, String> header = new HashMap<String, String>();
		header.put("field", field);
		header.put("label", label);
		return header;
	}
}