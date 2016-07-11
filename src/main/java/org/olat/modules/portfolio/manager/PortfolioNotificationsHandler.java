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
package org.olat.modules.portfolio.manager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.olat.basesecurity.IdentityNames;
import org.olat.core.commons.persistence.DB;
import org.olat.core.commons.services.notifications.NotificationsHandler;
import org.olat.core.commons.services.notifications.NotificationsManager;
import org.olat.core.commons.services.notifications.Publisher;
import org.olat.core.commons.services.notifications.Subscriber;
import org.olat.core.commons.services.notifications.SubscriptionInfo;
import org.olat.core.commons.services.notifications.model.SubscriptionListItem;
import org.olat.core.commons.services.notifications.model.TitleItem;
import org.olat.core.gui.translator.Translator;
import org.olat.core.id.Identity;
import org.olat.core.id.context.BusinessControlFactory;
import org.olat.core.util.StringHelper;
import org.olat.core.util.Util;
import org.olat.modules.portfolio.Binder;
import org.olat.modules.portfolio.PortfolioRoles;
import org.olat.modules.portfolio.ui.PortfolioHomeController;
import org.olat.user.UserManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import edu.emory.mathcs.backport.java.util.Collections;

/**
 * 
 * Initial date: 11.07.2016<br>
 * @author srosse, stephane.rosse@frentix.com, http://www.frentix.com
 *
 */
@Service
public class PortfolioNotificationsHandler implements NotificationsHandler {

	public static final String TYPE_NAME = Binder.class.getSimpleName();
	
	@Autowired
	private DB dbInstance;
	@Autowired
	private BinderDAO binderDao;
	@Autowired
	private UserManager userManager;

	@Override
	public SubscriptionInfo createSubscriptionInfo(Subscriber subscriber, Locale locale, Date compareDate) {

		SubscriptionInfo si = null;
		Publisher publisher = subscriber.getPublisher();
		Binder binder = binderDao.loadByKey(publisher.getResId());
		if (isInkoveValid(binder, compareDate, publisher)) {
			si = new SubscriptionInfo(subscriber.getKey(), publisher.getType(), getTitleItemForBinder(binder), null);
			List<SubscriptionListItem> allItems = getAllItems(binder, subscriber.getIdentity(), compareDate, locale);
			for (SubscriptionListItem item : allItems) {
				si.addSubscriptionListItem(item);
			}
		}

		if (si == null) {
			// no info, return empty
			si = NotificationsManager.getInstance().getNoSubscriptionInfo();
		}
		return si;
	}

	private boolean isInkoveValid(Binder binder, Date compareDate, Publisher publisher) {
		return (binder != null && compareDate != null && NotificationsManager.getInstance().isPublisherValid(publisher));
	}

	@Override
	public String createTitleInfo(Subscriber subscriber, Locale locale) {
		TitleItem title = getTitleItemForPublisher(subscriber.getPublisher());
		return title.getInfoContent("text/plain");
	}

	@Override
	public String getType() {
		return TYPE_NAME;
	}
	
	public List<SubscriptionListItem> getAllItems(Binder binder, Identity identity, Date compareDate, Locale locale) {
		String rootBusinessPath = "[HomeSite:" + identity.getKey() + "][PortfolioV2:2][Binder:" + binder.getKey() + "]";
		Translator translator = Util.createPackageTranslator(PortfolioHomeController.class, locale);
		
		List<SubscriptionListItem> items = new ArrayList<>();
		items.addAll(getCommentNotifications(binder, compareDate, rootBusinessPath, translator));
		items.addAll(getPageNotifications(binder, compareDate, rootBusinessPath, translator));
		Collections.sort(items, new PortfolioNotificationComparator());
		return items;
	}
	
	public List<SubscriptionListItem> getPageNotifications(Binder binder, Date compareDate,
			String rootBusinessPath, Translator translator) {
		
		StringBuilder sb = new StringBuilder();
		sb.append("select")
		  .append(" page.key as pageKey,")
		  .append(" page.title as pageTitle,")
		  .append(" page.creationDate as pageCreationDate,")
		  .append(" page.lastModified as pageLastModified,")
		  .append(" section.key as sectionKey,")
		  .append(" section.title as sectionTitle,")
		  .append(" section.creationDate as sectionCreationDate,")
		  .append(" section.lastModified as sectionLastModified")
		  .append(" from pfpage as page")
		  .append(" inner join page.section as section")
		  .append(" inner join section.binder as binder")
		  .append(" where binder.key=:binderKey and ")
		  .append(" (page.lastModified>=:compareDate or section.lastModified>=:compareDate)");
		
		List<Object[]> objects = dbInstance.getCurrentEntityManager()
				.createQuery(sb.toString(), Object[].class)
				.setParameter("binderKey", binder.getKey())
				.setParameter("compareDate", compareDate)
				.getResultList();
			
		Set<Long> uniqueSectionKeys = new HashSet<>();
		List<SubscriptionListItem> items = new ArrayList<>(objects.size());
		for (Object[] object : objects) {
			//page
			Long pageKey = (Long)object[0];
			String pageTitle = (String)object[1];
			Date pageCreationDate = (Date)object[2];
			Date pageLastModified = (Date)object[3];
			//section
			Long sectionKey = (Long)object[4];
			String sectionTitle = (String)object[5];
			Date sectionCreationDate = (Date)object[6];
			Date sectionLastModified = (Date)object[7];
			
			if(pageLastModified.compareTo(compareDate) >= 0) {
				String title;
				if(pageCreationDate.equals(pageLastModified)) {
					title = translator.translate("notifications.new.page", new String[]{ pageTitle });
				} else {
					title = translator.translate("notifications.modified.page", new String[]{ pageTitle });
				}
				
				String bPath = rootBusinessPath + "[Page:" + pageKey + "]";
				String linkUrl = BusinessControlFactory.getInstance().getURLFromBusinessPathString(bPath);
				SubscriptionListItem item = new SubscriptionListItem(title, linkUrl, bPath, pageLastModified, "o_icon_portfolio_page");
				item.setUserObject(pageKey);
				items.add(item);
			}
			
			if(!uniqueSectionKeys.contains(sectionKey) && sectionLastModified.compareTo(compareDate) >= 0) {
				String title;
				if(sectionCreationDate.equals(sectionLastModified)) {
					title = translator.translate("notifications.new.section", new String[]{ sectionTitle });
				} else {
					title = translator.translate("notifications.modified.section", new String[]{ sectionTitle });
				}
				
				String bPath = rootBusinessPath + "[Section:" + sectionKey + "]";
				String linkUrl = BusinessControlFactory.getInstance().getURLFromBusinessPathString(bPath);
				SubscriptionListItem item = new SubscriptionListItem(title, linkUrl, bPath, sectionLastModified, "o_icon_portfolio_section");
				item.setUserObject(sectionKey);
				items.add(item);

			}
			uniqueSectionKeys.add(sectionKey);
		}
		return items;
	}
	
	
	public List<SubscriptionListItem> getCommentNotifications(Binder binder, Date compareDate,
			String rootBusinessPath, Translator translator) {
		
		StringBuilder sb = new StringBuilder();
		sb.append("select")
		  .append(" comment.id as commentId,")
		  .append(" comment.creationDate as commentDate,")
		  .append(" page.key as pageKey,")
		  .append(" page.title as pageTitle,")
		  .append(" author.key as authorKey,")
		  .append(" author.name as authorName,")
		  .append(" authorUser.firstName as authorFirstName,")
		  .append(" authorUser.lastName as authorLastName")
		  .append(" from usercomment as comment")
		  .append(" inner join comment.creator as author")
		  .append(" inner join author.user as authorUser")
		  .append(" inner join pfpage as page on (comment.resId=page.key and comment.resName='Page')")
		  .append(" inner join pfsection as section on (section.key = page.section.key)")
		  .append(" inner join pfbinder as binder on (binder.key=section.binder.key)")
		  .append(" where binder.key=:binderKey and comment.creationDate>=:compareDate");
	
		List<Object[]> objects = dbInstance.getCurrentEntityManager()
			.createQuery(sb.toString(), Object[].class)
			.setParameter("binderKey", binder.getKey())
			.setParameter("compareDate", compareDate)
			.getResultList();
		
		List<SubscriptionListItem> items = new ArrayList<>(objects.size());
		for (Object[] object : objects) {
			Long commentId = (Long)object[0];
			Date commentDate = (Date)object[1];
			Long pageKey = (Long)object[2];
			String pageTitle = (String)object[3];
			NotificationIdentityNames author = getIdentityNames(object, 4);

			String bPath = rootBusinessPath + "[Page:" + pageKey + "][Comment:" + commentId + "]";
			String linkUrl = BusinessControlFactory.getInstance().getURLFromBusinessPathString(bPath);
			String[] title = new String[] { pageTitle, userManager.getUserDisplayName(author) };
			SubscriptionListItem item = new SubscriptionListItem(translator.translate("notifications.new.comment", title), linkUrl, bPath, commentDate, "o_icon_comments");
			item.setUserObject(pageKey);
			items.add(item);
		}
		return items;
	}
	
	private NotificationIdentityNames getIdentityNames(Object[] object, int startIndex) {
		Long key = (Long)object[startIndex++];
		String name = (String)object[startIndex++];
		String firstName = (String)object[startIndex++];
		String lastName = (String)object[startIndex++];
		return new NotificationIdentityNames(key, name, firstName, lastName);
	}
	
	/**
	 * returns a TitleItem instance for the given Publisher p If you already
	 * have a reference to the map, use
	 * <code>getTitleItemForMap(EPAbstractMap amap)</code>
	 * 
	 * @param p
	 * @return
	 */
	private TitleItem getTitleItemForPublisher(Publisher p) {
		Binder binder = binderDao.loadByKey(p.getResId());
		return getTitleItemForBinder(binder);
	}

	/**
	 * returns a TitleItem instance for the given AbstractMap
	 * 
	 * @param amap
	 * @return
	 */
	private TitleItem getTitleItemForBinder(Binder binder) {
		StringBuilder sb = new StringBuilder();
		if (binder != null) {
			sb.append(StringHelper.escapeHtml(binder.getTitle()));
			List<Identity> owners = binderDao.getMembers(binder, PortfolioRoles.owner.name());
					
			if(owners.size() > 0) {
				sb.append(" (");
				for(int i=0; i<owners.size(); i++) {
					if(i > 0) sb.append(", ");
					String fullname = userManager.getUserDisplayName(owners.get(0));
					sb.append(StringHelper.escapeHtml(fullname));
				}
				sb.append(")");
			}
		}
		return new TitleItem(sb.toString(), "o_icon_portfolio_binder");
	}
	
	public static class NotificationIdentityNames implements IdentityNames {
		
		private final Long key;
		private final String name;
		private final String firstName;
		private final String lastName;
		
		public NotificationIdentityNames(Long key, String name, String firstName, String lastName) {
			this.key = key;
			this.name = name;
			this.firstName = firstName;
			this.lastName = lastName;
		}

		@Override
		public Long getKey() {
			return key;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getFirstName() {
			return firstName;
		}

		@Override
		public String getLastName() {
			return lastName;
		}
	}
	
	public static class PortfolioNotificationComparator implements Comparator<SubscriptionListItem> {
		
		@Override
		public int compare(SubscriptionListItem o1, SubscriptionListItem o2) {
			return -o1.getDate().compareTo(o2.getDate());
		}

	}
}
