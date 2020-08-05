/**
 * <a href="http://www.openolat.org">
 * OpenOLAT - Online Learning and Training</a><br>
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); <br>
 * you may not use this file except in compliance with the License.<br>
 * You may obtain a copy of the License at the
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache homepage</a>
 * <p>
 * Unless required by applicable law or agreed to in writing,<br>
 * software distributed under the License is distributed on an "AS IS" BASIS, <br>
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. <br>
 * See the License for the specific language governing permissions and <br>
 * limitations under the License.
 * <p>
 * Initial code contributed and copyrighted by<br>
 * frentix GmbH, http://www.frentix.com
 * <p>
 */
package org.olat.user.manager.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;
import org.olat.basesecurity.BaseSecurity;
import org.olat.basesecurity.BaseSecurityModule;
import org.olat.basesecurity.GroupRoles;
import org.olat.basesecurity.IdentityImpl;
import org.olat.basesecurity.manager.IdentityDAO;
import org.olat.core.commons.persistence.DB;
import org.olat.core.id.Identity;
import org.olat.core.id.OLATResourceable;
import org.olat.core.id.Roles;
import org.olat.core.id.User;
import org.olat.core.id.UserConstants;
import org.olat.core.util.DateUtils;
import org.olat.core.util.StringHelper;
import org.olat.course.CourseFactory;
import org.olat.course.config.CourseConfig;
import org.olat.course.disclaimer.CourseDisclaimerManager;
import org.olat.group.BusinessGroup;
import org.olat.group.BusinessGroupService;
import org.olat.repository.RepositoryEntry;
import org.olat.repository.RepositoryService;
import org.olat.test.JunitTestHelper;
import org.olat.test.OlatTestCase;
import org.olat.user.UserManager;
import org.olat.user.UserModule;
import org.springframework.beans.factory.annotation.Autowired;

import com.dumbster.smtp.SmtpMessage;

/**
 * 
 * Initial date: 2 juin 2020<br>
 * @author srosse, stephane.rosse@frentix.com, http://www.frentix.com
 *
 */
public class UserLifecycleManagerTest extends OlatTestCase {
	
	@Autowired
	private DB dbInstance;
	@Autowired
	private UserModule userModule;
	@Autowired
	private IdentityDAO identityDao;
	@Autowired
	private UserManager userManager;
	@Autowired
	private BaseSecurity securityManager;
	@Autowired
	private RepositoryService repositoryService;
	@Autowired
	private UserLifecycleManagerImpl lifecycleManager;
	@Autowired
	private BusinessGroupService businessGroupService;
	@Autowired
	private CourseDisclaimerManager courseDisclaimerManager;
	
	/**
	 * The search is limited to status active, pending and login denied.
	 * 
	 * @param loginDate Search identity with last login before the specified date
	 * @return A list of identities 
	 */
	@Test
	public void getReadyToInactivateIdentities() {
		Identity id1 = JunitTestHelper.createAndPersistIdentityAsRndUser("lifecycle-1");
		identityDao.setIdentityLastLogin(id1, DateUtils.addDays(new Date(), -910));

		Identity id2 = JunitTestHelper.createAndPersistIdentityAsRndUser("lifecycle-1");
		identityDao.setIdentityLastLogin(id2, DateUtils.addDays(new Date(), -710));
		dbInstance.commitAndCloseSession();
		
		Date beforeDate = DateUtils.addDays(new Date(), -900);
		List<Identity> identitiesToInactivate = lifecycleManager.getReadyToInactivateIdentities(beforeDate);
		Assert.assertNotNull(identitiesToInactivate);
		Assert.assertTrue(identitiesToInactivate.contains(id1));
		Assert.assertFalse(identitiesToInactivate.contains(id2));
		for(Identity identityToInactivate:identitiesToInactivate) {
			Date lastLoginDate = identityToInactivate.getLastLogin();
			if(lastLoginDate == null) {
				lastLoginDate = identityToInactivate.getCreationDate();
			}
			
			Assert.assertTrue(lastLoginDate.before(beforeDate));
			assertThat(identityToInactivate.getStatus())
				.isIn(Identity.STATUS_ACTIV, Identity.STATUS_PENDING, Identity.STATUS_LOGIN_DENIED);
		}
	}
	
	@Test
	public void getIdentitiesToInactivate() {
		Identity id1 = JunitTestHelper.createAndPersistIdentityAsRndUser("lifecycle-3");
		identityDao.setIdentityLastLogin(id1, DateUtils.addDays(new Date(), -905));
		securityManager.saveIdentityStatus(id1, Identity.STATUS_PENDING, null);

		Identity id2 = JunitTestHelper.createAndPersistIdentityAsRndUser("lifecycle-4");
		identityDao.setIdentityLastLogin(id2, DateUtils.addDays(new Date(), -908));
		securityManager.saveIdentityStatus(id2, Identity.STATUS_PERMANENT, null);

		dbInstance.commitAndCloseSession();
		
		Date beforeDate = DateUtils.addDays(new Date(), -900);
		List<Identity> identitiesToInactivate = lifecycleManager.getIdentitiesToInactivate(beforeDate, null);
		Assert.assertNotNull(identitiesToInactivate);
		Assert.assertTrue(identitiesToInactivate.contains(id1));
		Assert.assertFalse(identitiesToInactivate.contains(id2));
		
		for(Identity identityToInactivate:identitiesToInactivate) {
			Date lastLoginDate = identityToInactivate.getLastLogin();
			if(lastLoginDate == null) {
				lastLoginDate = identityToInactivate.getCreationDate();
			}
			
			Assert.assertTrue(lastLoginDate.before(beforeDate));
			assertThat(identityToInactivate.getStatus())
				.isIn(Identity.STATUS_ACTIV, Identity.STATUS_PENDING, Identity.STATUS_LOGIN_DENIED);
		}
	}
	
	@Test
	public void getIdentitiesToDelete() {
		Identity id1 = JunitTestHelper.createAndPersistIdentityAsRndUser("lifecycle-5");
		identityDao.setIdentityLastLogin(id1, DateUtils.addDays(new Date(), -1205));
		id1 = securityManager.saveIdentityStatus(id1, Identity.STATUS_INACTIVE, null);
		((IdentityImpl)id1).setInactivationDate(DateUtils.addDays(new Date(), -240));
		id1 = identityDao.saveIdentity(id1);

		Identity id2 = JunitTestHelper.createAndPersistIdentityAsRndUser("lifecycle-6");
		identityDao.setIdentityLastLogin(id2, DateUtils.addDays(new Date(), -1308));
		id2 = securityManager.saveIdentityStatus(id2, Identity.STATUS_PERMANENT, null);

		dbInstance.commitAndCloseSession();
		
		Date beforeDate = DateUtils.addDays(new Date(), -180);
		List<Identity> identitiesToInactivate = lifecycleManager.getIdentitiesToDelete(beforeDate, null);
		Assert.assertNotNull(identitiesToInactivate);
		Assert.assertTrue(identitiesToInactivate.contains(id1));
		Assert.assertFalse(identitiesToInactivate.contains(id2));
		
		for(Identity identityToInactivate:identitiesToInactivate) {
			Date inactivationDate = identityToInactivate.getInactivationDate();
			Assert.assertNotNull(inactivationDate);
			Assert.assertTrue(inactivationDate.before(beforeDate));
			Assert.assertEquals(Identity.STATUS_INACTIVE, identityToInactivate.getStatus());
		}
	}
	
	@Test
	public void getReadyToDeleteIdentities() {
		Identity id1 = JunitTestHelper.createAndPersistIdentityAsRndUser("lifecycle-7");
		identityDao.setIdentityLastLogin(id1, DateUtils.addDays(new Date(), -1205));
		id1 = securityManager.saveIdentityStatus(id1, Identity.STATUS_INACTIVE, null);
		((IdentityImpl)id1).setInactivationDate(DateUtils.addDays(new Date(), -240));// fake inactivation date
		id1 = identityDao.saveIdentity(id1);

		Identity id2 = JunitTestHelper.createAndPersistIdentityAsRndUser("lifecycle-8");
		identityDao.setIdentityLastLogin(id2, DateUtils.addDays(new Date(), -1308));
		id2 = securityManager.saveIdentityStatus(id2, Identity.STATUS_PENDING, null);

		dbInstance.commitAndCloseSession();
		
		Date beforeDate = DateUtils.addDays(new Date(), -180);
		List<Identity> identitiesToInactivate = lifecycleManager.getReadyToDeleteIdentities(beforeDate);
		Assert.assertNotNull(identitiesToInactivate);
		Assert.assertTrue(identitiesToInactivate.contains(id1));
		Assert.assertFalse(identitiesToInactivate.contains(id2));
		
		for(Identity identityToInactivate:identitiesToInactivate) {
			Date inactivationDate = identityToInactivate.getInactivationDate();
			Assert.assertNotNull(inactivationDate);
			Assert.assertTrue(inactivationDate.before(beforeDate));
			Assert.assertEquals(Identity.STATUS_INACTIVE, identityToInactivate.getStatus());
		}
	}
	
	@Test
	public void inactivateIdentities() {
		Assert.assertTrue(userModule.isUserAutomaticDeactivation());
		
		Identity id1 = JunitTestHelper.createAndPersistIdentityAsRndUser("lifecycle-9");
		identityDao.setIdentityLastLogin(id1, DateUtils.addDays(new Date(), -1205));
		id1 = securityManager.saveIdentityStatus(id1, Identity.STATUS_ACTIV, null);

		Identity id2 = JunitTestHelper.createAndPersistIdentityAsRndUser("lifecycle-10");
		identityDao.setIdentityLastLogin(id2, DateUtils.addDays(new Date(), -1308));
		id2 = securityManager.saveIdentityStatus(id2, Identity.STATUS_PENDING, null);

		dbInstance.commitAndCloseSession();
		
		Set<Identity> vetoed = new HashSet<>();
		lifecycleManager.inactivateIdentities(vetoed);
		dbInstance.commitAndCloseSession();
		
		// vetoed for the rest of the process
		Assert.assertTrue(vetoed.contains(id1));
		Assert.assertTrue(vetoed.contains(id2));
		
		// email is enabled
		Identity informedId1 = securityManager.loadIdentityByKey(id1.getKey());
		Assert.assertEquals(Identity.STATUS_ACTIV, informedId1.getStatus());
		Assert.assertNull(informedId1.getInactivationDate());
		Assert.assertNotNull(((IdentityImpl)informedId1).getInactivationEmailDate());
		
		Identity informedId2 = securityManager.loadIdentityByKey(id2.getKey());
		Assert.assertNull(informedId2.getInactivationDate());
		Assert.assertEquals(Identity.STATUS_PENDING, informedId2.getStatus());
		Assert.assertNotNull(((IdentityImpl)informedId2).getInactivationEmailDate());
		
		// Artificially mail in past
		((IdentityImpl)informedId1).setInactivationEmailDate(DateUtils.addDays(new Date(), -31));
		informedId1 = identityDao.saveIdentity(informedId1);
		((IdentityImpl)informedId2).setInactivationEmailDate(DateUtils.addDays(new Date(), -31));
		informedId2 = identityDao.saveIdentity(informedId2);
		
		// check mails sent
		List<SmtpMessage> messages = getSmtpServer().getReceivedEmails();
		Assert.assertTrue(messages.size() >= 2);
		getSmtpServer().reset();

		Set<Identity> vetoed2 = new HashSet<>();
		lifecycleManager.inactivateIdentities(vetoed2);
		dbInstance.commitAndCloseSession();
		
		Identity inactiveId1 = securityManager.loadIdentityByKey(id1.getKey());
		Assert.assertEquals(Identity.STATUS_INACTIVE, inactiveId1.getStatus());
		Assert.assertNotNull(inactiveId1.getInactivationDate());
		Assert.assertNotNull(((IdentityImpl)inactiveId1).getInactivationEmailDate());
		
		Identity inactiveId2 = securityManager.loadIdentityByKey(id1.getKey());
		Assert.assertEquals(Identity.STATUS_INACTIVE, inactiveId2.getStatus());
		Assert.assertNotNull(inactiveId2.getInactivationDate());
		Assert.assertNotNull(((IdentityImpl)inactiveId2).getInactivationEmailDate());
		
		// check mails sent
		List<SmtpMessage> inactivedMessages = getSmtpServer().getReceivedEmails();
		Assert.assertTrue(inactivedMessages.size() >= 2);
	}
	
	@Test
	public void deleteIdentities() {
		Assert.assertTrue(userModule.isUserAutomaticDeletion());
		
		Identity id1 = JunitTestHelper.createAndPersistIdentityAsRndUser("lifecycle-11");
		identityDao.setIdentityLastLogin(id1, DateUtils.addDays(new Date(), -1205));
		id1 = securityManager.saveIdentityStatus(id1, Identity.STATUS_INACTIVE, null);
		((IdentityImpl)id1).setInactivationDate(DateUtils.addDays(new Date(), -1000));
		((IdentityImpl)id1).setInactivationEmailDate(DateUtils.addDays(new Date(), -1030));
		id1 = identityDao.saveIdentity(id1);

		Identity id2 = JunitTestHelper.createAndPersistIdentityAsRndUser("lifecycle-12");
		identityDao.setIdentityLastLogin(id2, DateUtils.addDays(new Date(), -1308));
		id2 = securityManager.saveIdentityStatus(id2, Identity.STATUS_PENDING, null);

		dbInstance.commitAndCloseSession();

		Set<Identity> vetoed = new HashSet<>();
		lifecycleManager.deleteIdentities(vetoed);
		dbInstance.commitAndCloseSession();
		
		// vetoed for the rest of the process
		Assert.assertTrue(vetoed.contains(id1));
		Assert.assertFalse(vetoed.contains(id2));
		
		// email is enabled
		Identity informedId1 = securityManager.loadIdentityByKey(id1.getKey());
		Assert.assertEquals(Identity.STATUS_INACTIVE, informedId1.getStatus());
		Assert.assertNotNull(informedId1.getInactivationDate());
		Assert.assertNotNull(((IdentityImpl)informedId1).getInactivationEmailDate());
		Assert.assertNotNull(((IdentityImpl)informedId1).getDeletionEmailDate());
		
		Identity pendingId2 = securityManager.loadIdentityByKey(id2.getKey());
		Assert.assertEquals(Identity.STATUS_PENDING, pendingId2.getStatus());

		// check mails sent
		List<SmtpMessage> messages = getSmtpServer().getReceivedEmails();
		Assert.assertTrue(messages.size() >= 1);
		getSmtpServer().reset();
		
		// set email in the past
		((IdentityImpl)informedId1).setDeletionEmailDate(DateUtils.addDays(new Date(), -31));
		informedId1 = identityDao.saveIdentity(informedId1);
		
		Set<Identity> vetoed2 = new HashSet<>();
		lifecycleManager.deleteIdentities(vetoed2);
		dbInstance.commitAndCloseSession();
		
		Identity deletedId1 = securityManager.loadIdentityByKey(id1.getKey());
		Assert.assertEquals(Identity.STATUS_DELETED, deletedId1.getStatus());
		Assert.assertNotNull(((IdentityImpl)deletedId1).getDeletedDate());
		Assert.assertNotNull(((IdentityImpl)deletedId1).getDeletionEmailDate());
		
		pendingId2 = securityManager.loadIdentityByKey(id2.getKey());
		Assert.assertEquals(Identity.STATUS_PENDING, pendingId2.getStatus());

		// check mails sent
		List<SmtpMessage> inactivedMessages = getSmtpServer().getReceivedEmails();
		Assert.assertTrue(inactivedMessages.size() >= 1);
	}
	
	
	/**
	 * The test simulates the automatic inactivation and deletion
	 * process with several identities in different steps of the
	 * process.
	 */
	@Test
	public void deleteIdentitiesProcess() {
		Identity staleIdentity = JunitTestHelper.createAndPersistIdentityAsRndUser("lifecycle-14");
		identityDao.setIdentityLastLogin(staleIdentity, DateUtils.addDays(new Date(), -1205));
		staleIdentity = securityManager.saveIdentityStatus(staleIdentity, Identity.STATUS_ACTIV, null);
		
		Identity readyToInactivate = JunitTestHelper.createAndPersistIdentityAsRndUser("lifecycle-14");
		identityDao.setIdentityLastLogin(readyToInactivate, DateUtils.addDays(new Date(), -1205));
		readyToInactivate = securityManager.saveIdentityStatus(readyToInactivate, Identity.STATUS_ACTIV, null);
		((IdentityImpl)readyToInactivate).setInactivationEmailDate(DateUtils.addDays(new Date(), -1030));
		readyToInactivate = identityDao.saveIdentity(readyToInactivate);
		
		Identity readyToDelete = JunitTestHelper.createAndPersistIdentityAsRndUser("lifecycle-15");
		identityDao.setIdentityLastLogin(readyToDelete, DateUtils.addDays(new Date(), -1205));
		readyToDelete = securityManager.saveIdentityStatus(readyToDelete, Identity.STATUS_INACTIVE, null);
		((IdentityImpl)readyToDelete).setInactivationDate(DateUtils.addDays(new Date(), -1000));
		((IdentityImpl)readyToDelete).setInactivationEmailDate(DateUtils.addDays(new Date(), -1030));
		readyToDelete = identityDao.saveIdentity(readyToDelete);
		
		Identity deleteMailSent = JunitTestHelper.createAndPersistIdentityAsRndUser("lifecycle-16");
		identityDao.setIdentityLastLogin(deleteMailSent, DateUtils.addDays(new Date(), -1205));
		deleteMailSent = securityManager.saveIdentityStatus(deleteMailSent, Identity.STATUS_INACTIVE, null);
		((IdentityImpl)deleteMailSent).setInactivationDate(DateUtils.addDays(new Date(), -1000));
		((IdentityImpl)deleteMailSent).setInactivationEmailDate(DateUtils.addDays(new Date(), -1030));
		((IdentityImpl)deleteMailSent).setDeletionEmailDate(DateUtils.addDays(new Date(), -1090));
		deleteMailSent = identityDao.saveIdentity(deleteMailSent);

		Identity pendingUser = JunitTestHelper.createAndPersistIdentityAsRndUser("lifecycle-17");
		identityDao.setIdentityLastLogin(pendingUser, DateUtils.addDays(new Date(), -1308));
		pendingUser = securityManager.saveIdentityStatus(pendingUser, Identity.STATUS_PENDING, null);

		dbInstance.commitAndCloseSession();
		
		// start the delete process
		Set<Identity> vetoed = new HashSet<>();
		lifecycleManager.inactivateIdentities(vetoed);
		lifecycleManager.deleteIdentities(vetoed);
		dbInstance.commitAndCloseSession();
		
		// stale identity get a mail
		Identity inactiveMailed = securityManager.loadIdentityByKey(staleIdentity.getKey());
		Assert.assertEquals(Identity.STATUS_ACTIV, inactiveMailed.getStatus());
		Assert.assertNull(inactiveMailed.getInactivationDate());
		Assert.assertNotNull(((IdentityImpl)inactiveMailed).getInactivationEmailDate());
		
		// inactive mailed user get inactivated identity get a mail
		Identity inactiveIdentity = securityManager.loadIdentityByKey(readyToInactivate.getKey());
		Assert.assertEquals(Identity.STATUS_INACTIVE, inactiveIdentity.getStatus());
		Assert.assertNotNull(inactiveIdentity.getInactivationDate());
		Assert.assertNotNull(((IdentityImpl)inactiveIdentity).getInactivationEmailDate());
		
		// ready to delete get a mail
		Identity mailedDeleteId = securityManager.loadIdentityByKey(readyToDelete.getKey());
		Assert.assertEquals(Identity.STATUS_INACTIVE, mailedDeleteId.getStatus());
		Assert.assertNull(((IdentityImpl)mailedDeleteId).getDeletedDate());
		Assert.assertNotNull(((IdentityImpl)mailedDeleteId).getDeletionEmailDate());
		
		// user was get a deletion mail is deleted
		Identity deleteId = securityManager.loadIdentityByKey(deleteMailSent.getKey());
		Assert.assertEquals(Identity.STATUS_DELETED, deleteId.getStatus());
		Assert.assertNotNull(((IdentityImpl)deleteId).getDeletedDate());
		Assert.assertNotNull(((IdentityImpl)deleteId).getDeletionEmailDate());
		
		// pending user is still pending
		pendingUser = securityManager.loadIdentityByKey(pendingUser.getKey());
		Assert.assertEquals(Identity.STATUS_PENDING, pendingUser.getStatus());
	}
	
	
	@Test
	public void deleteIdentity() {
		String username = "id-to-del-" + UUID.randomUUID();
		String email = username + "@frentix.com";
		User user = userManager.createUser("first" + username, "last" + username, email);
		user.setProperty(UserConstants.COUNTRY, "");
		user.setProperty(UserConstants.CITY, "Basel");
		user.setProperty(UserConstants.INSTITUTIONALNAME, "Del-23");
		user.setProperty(UserConstants.INSTITUTIONALUSERIDENTIFIER, "Del-24");
		Identity identity = securityManager.createAndPersistIdentityAndUser(username, null, user, BaseSecurityModule.getDefaultAuthProviderIdentifier(), username, "secret");
		dbInstance.commitAndCloseSession();
		// add some stuff
		
		//a group
		BusinessGroup group = businessGroupService.createBusinessGroup(identity, "Group", "Group", -1, -1, false, false, null);
		Assert.assertNotNull(group);
		dbInstance.commit();
		//a course
		RepositoryEntry course = JunitTestHelper.deployBasicCourse(identity);
		dbInstance.commitAndCloseSession();
		//a course disclaimer
		OLATResourceable courseOres = course.getOlatResource();
		CourseConfig courseConfig = CourseFactory.openCourseEditSession(courseOres.getResourceableId()).getCourseEnvironment().getCourseConfig();
		courseConfig.setDisclaimerEnabled(1, true);
		courseConfig.setDisclaimerEnabled(2, true);
		CourseFactory.setCourseConfig(courseOres.getResourceableId(), courseConfig);
		CourseFactory.saveCourse(courseOres.getResourceableId());
		CourseFactory.closeCourseEditSession(courseOres.getResourceableId(), true);
		//a consent to the disclaimer
		courseDisclaimerManager.acceptDisclaimer(course, identity, true, true);
		
		Assert.assertTrue(courseDisclaimerManager.isAccessGranted(course, identity));		
		Assert.assertEquals(identity.getName(), course.getInitialAuthor());
		Assert.assertTrue(repositoryService.hasRoleExpanded(identity, GroupRoles.owner.name()));
		assertThat(courseDisclaimerManager.getConsents(course)).hasSize(1);
		
		//delete the identity
		lifecycleManager.deleteIdentity(identity, null);
		dbInstance.commit();

		//check
		Identity deletedIdentity = securityManager.loadIdentityByKey(identity.getKey());
		Assert.assertNotNull(deletedIdentity);
		
		//check membership of group
		boolean isMember = businessGroupService.isIdentityInBusinessGroup(deletedIdentity, group);
		Assert.assertFalse(isMember);
		RepositoryEntry reloadedCourse = repositoryService.loadByKey(course.getKey());
		Assert.assertFalse(reloadedCourse.getInitialAuthor().equals(username));
		boolean isOwner = repositoryService.hasRoleExpanded(identity, GroupRoles.owner.name());
		Assert.assertFalse(isOwner);
		
		//check deleted consents
		assertThat(courseDisclaimerManager.getConsents(course)).hasSize(0);
		
		User deletedUser = deletedIdentity.getUser();
		// process keep first name last name from user with some "administrative"
		Assert.assertEquals("first" + username, deletedUser.getProperty(UserConstants.FIRSTNAME, null));
		Assert.assertEquals("last" + username, deletedUser.getProperty(UserConstants.LASTNAME, null));
		// but not the other properties
		String institutionalName = deletedUser.getProperty(UserConstants.INSTITUTIONALNAME, null);
		Assert.assertFalse(StringHelper.containsNonWhitespace(institutionalName));
		String institutionalId = deletedUser.getProperty(UserConstants.INSTITUTIONALUSERIDENTIFIER, null);
		Assert.assertFalse(StringHelper.containsNonWhitespace(institutionalId));
		String deletedEmail = deletedUser.getProperty(UserConstants.EMAIL, null);
		Assert.assertFalse(StringHelper.containsNonWhitespace(deletedEmail));
	}
	
	/**
	 * The test checked that all of the user properties are wiped out.
	 * 
	 */
	@Test
	public void deleteIdentity_noRoles() {
		Identity groupCoach = JunitTestHelper.createAndPersistIdentityAsRndUser("del-6");
		
		String username = "id-to-del-2-" + UUID.randomUUID();
		String email = username + "@frentix.com";
		User user = userManager.createUser("first" + username, "last" + username, email);
		user.setProperty(UserConstants.COUNTRY, "");
		user.setProperty(UserConstants.CITY, "Basel");
		user.setProperty(UserConstants.INSTITUTIONALNAME, "Del-23");
		user.setProperty(UserConstants.INSTITUTIONALUSERIDENTIFIER, "Del-24");
		Identity identity = securityManager.createAndPersistIdentityAndUser(username, null, user, BaseSecurityModule.getDefaultAuthProviderIdentifier(), username, "secret");
		dbInstance.commitAndCloseSession();

		//a group
		Roles coachRolesId = securityManager.getRoles(groupCoach);
		BusinessGroup group = businessGroupService.createBusinessGroup(groupCoach, "Group", "Group", -1, -1, false, false, null);
		dbInstance.commit();
		businessGroupService.addParticipants(groupCoach, coachRolesId, Collections.singletonList(identity), group, null);
		dbInstance.commit();
		
		//delete the identity
		lifecycleManager.deleteIdentity(identity, groupCoach);
		dbInstance.commit();
		
		IdentityImpl deletedIdentity = (IdentityImpl)securityManager.loadIdentityByKey(identity.getKey());
		Assert.assertNotNull(deletedIdentity);
		Assert.assertNotNull(deletedIdentity.getDeletedDate());
		Assert.assertEquals(groupCoach.getUser().getLastName() + ", " + groupCoach.getUser().getFirstName(), deletedIdentity.getDeletedBy());

		User deletedUser = deletedIdentity.getUser();
		Assert.assertFalse(StringHelper.containsNonWhitespace(deletedUser.getProperty(UserConstants.FIRSTNAME, null)));
		Assert.assertFalse(StringHelper.containsNonWhitespace(deletedUser.getProperty(UserConstants.LASTNAME, null)));
		Assert.assertFalse(StringHelper.containsNonWhitespace(deletedUser.getProperty(UserConstants.INSTITUTIONALNAME, null)));
		Assert.assertFalse(StringHelper.containsNonWhitespace(deletedUser.getProperty(UserConstants.INSTITUTIONALUSERIDENTIFIER, null)));
		Assert.assertFalse(StringHelper.containsNonWhitespace(deletedUser.getProperty(UserConstants.EMAIL, null)));
	}

}
