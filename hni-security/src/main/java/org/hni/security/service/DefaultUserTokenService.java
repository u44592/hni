package org.hni.security.service;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Inject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hni.organization.om.UserOrganizationRole;
import org.hni.organization.service.OrganizationUserService;
import org.hni.security.dao.SecretDAO;
import org.hni.security.om.AuthorizedUser;
import org.hni.security.om.Encryption;
import org.hni.security.om.OrganizationUserRolePermission;
import org.hni.security.om.Permission;
import org.hni.security.om.UserToken;
import org.hni.user.om.User;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Represents a permission (ability for a user to perform a given action). These
 * are linked to users in organizations via roles.
 */

public class DefaultUserTokenService implements UserTokenService {
	private static final Log LOG = LogFactory.getLog(UserTokenService.class);
	public static final String DELIMITER = "|";
	public static final String PERMISSION_FIELD_DELIMITER = ":";
	public static final String PERMISSION_DELIMITER = "/";
	private RolePermissionService rolePermissionService;
	private OrganizationUserService orgUserService;

	private static Encryption encryption;

	@Inject
	public DefaultUserTokenService(SecretDAO secretDAO, RolePermissionService rolePermissionService, OrganizationUserService orgUserService) {
		this.rolePermissionService = rolePermissionService;
		this.orgUserService = orgUserService;
		if (null == encryption) {
			encryption = new Encryption(secretDAO.get(1L).getSecret());
		}
	}

	public Set<OrganizationUserRolePermission> getUserOrganizationRolePermissions(User user, Long organizationId) {
		Set<UserOrganizationRole> userOrgRoles = new TreeSet<UserOrganizationRole>();
		Collection<UserOrganizationRole> userOrganizationRoles = orgUserService.getUserOrganizationRoles(user);
		for (UserOrganizationRole userOrgRole : userOrganizationRoles) {
			if (organizationId.equals(userOrgRole.getId().getOrgId())) {
				userOrgRoles.add(userOrgRole);
			}
		}
		Set<OrganizationUserRolePermission> orgPermissions = new TreeSet<OrganizationUserRolePermission>();
		for (UserOrganizationRole userOrgRole : userOrgRoles) {
			OrganizationUserRolePermission orgUserRolePermission = new OrganizationUserRolePermission();
			orgUserRolePermission.setOrganizationId(organizationId);
			orgUserRolePermission.setRoleId(userOrgRole.getId().getRoleId());
			orgUserRolePermission.setUserId(user.getId());
			Set<Permission> permissions = rolePermissionService.byRoleId(userOrgRole.getId().getRoleId(), userOrgRole.getId().getOrgId());
			orgUserRolePermission.setPermissions(permissions);
		}
		return orgPermissions;
	}

	public String getUserToke(User user, Long organizationId) {
		UserToken userToken = new UserToken();
		Set<OrganizationUserRolePermission> orgPermissions = getUserOrganizationRolePermissions(user, organizationId);
		userToken.setOrganizationUserRolePermissions(orgPermissions);
		userToken.setUserIdentifier(user.getEmail());
		userToken.setOrganiationId(organizationId);
		userToken.setCreateTime(System.currentTimeMillis());
		userToken.setExpirationTime(System.currentTimeMillis() + 60 * 60 * 1000);
		String tokenString = stringify(userToken);
		String encryptedToken = encryption.encrypt(tokenString);
		/* encrypt token */
		return encryptedToken;
	}

	private static final ObjectMapper objectMapper = new ObjectMapper();

	private String stringify(Object o) {
		String response = "";
		try {
			response = objectMapper.writeValueAsString(o);
		} catch (JsonProcessingException e) {
			LOG.warn(e, e);
		}
		return response;
	}

	private <T> T parseJson(String json, Class<T> clazz) {
		T t = null;
		try {
			t = objectMapper.readValue(json, clazz);
		} catch (IOException e) {
			LOG.warn(e, e);
		}
		return t;
	}

	public AuthorizedUser getTokenUser(String token) {
		/* decrypt encrypted token */
		String decryptedToken = encryption.decrypt(token);
		UserToken userToken = parseJson(decryptedToken, UserToken.class);
		User userx = orgUserService.byEmailAddress(userToken.getUserIdentifier());
		AuthorizedUser user = null;
		/* if token not expired, and user found */
		/* && !users . isEmpty () */
		if (System.currentTimeMillis() < userToken.getExpirationTime()) {
			user = new AuthorizedUser(userx);
			user.setPermissions(userToken.getOrganizationUserRolePermissions());
			user.setOrgId(userToken.getOrganiationId());
			user.getUser().setToken(token);
			user.setTokenCreationTime(userToken.getCreateTime());
			user.setTokenExpirationTime(userToken.getExpirationTime());
		} else {
			user = new AuthorizedUser();
		}
		return user;
	}
}