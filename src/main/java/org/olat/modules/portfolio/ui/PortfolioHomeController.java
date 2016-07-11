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
package org.olat.modules.portfolio.ui;

import java.util.ArrayList;
import java.util.List;

import org.olat.core.gui.UserRequest;
import org.olat.core.gui.components.Component;
import org.olat.core.gui.components.link.Link;
import org.olat.core.gui.components.link.LinkFactory;
import org.olat.core.gui.components.stack.TooledStackedPanel;
import org.olat.core.gui.components.velocity.VelocityContainer;
import org.olat.core.gui.control.Event;
import org.olat.core.gui.control.WindowControl;
import org.olat.core.gui.control.controller.BasicController;
import org.olat.core.gui.control.generic.dtabs.Activateable2;
import org.olat.core.id.OLATResourceable;
import org.olat.core.id.context.BusinessControlFactory;
import org.olat.core.id.context.ContextEntry;
import org.olat.core.id.context.StateEntry;
import org.olat.core.util.resource.OresHelper;
import org.olat.modules.portfolio.Binder;
import org.olat.modules.portfolio.BinderRef;
import org.olat.modules.portfolio.BinderSecurityCallback;
import org.olat.modules.portfolio.BinderSecurityCallbackFactory;
import org.olat.modules.portfolio.Page;
import org.olat.modules.portfolio.PortfolioRoles;
import org.olat.modules.portfolio.PortfolioService;
import org.olat.modules.portfolio.model.BinderRefImpl;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 
 * Initial date: 06.06.2016<br>
 * @author srosse, stephane.rosse@frentix.com, http://www.frentix.com
 *
 */
public class PortfolioHomeController extends BasicController implements Activateable2 {
	
	private Link myBindersLink, myEntriesLink, mySharedItemsLink, sharedItemsLink, mediaCenterLink;
	private Link editLastEntryLink, createNewEntryLink, editLastUsedBinderLink, goToTrashLink;
	private final VelocityContainer mainVC;
	private final TooledStackedPanel stackPanel;
	
	private MyPageListController myPageListCtrl;
	private MediaCenterController mediaCenterCtrl;
	private SharedItemsController sharedWithMeCtrl;
	private BinderListController myPortfolioListCtrl;
	private MySharedItemsController mySharedItemsCtrl;
	
	@Autowired
	private PortfolioService portfolioService;
	
	public PortfolioHomeController(UserRequest ureq, WindowControl wControl, TooledStackedPanel stackPanel) {
		super(ureq, wControl);
		this.stackPanel = stackPanel;
		stackPanel.setToolbarAutoEnabled(true);

		mainVC = createVelocityContainer("home");
		myBindersLink = LinkFactory.createLink("goto.my.binders", mainVC, this);
		myBindersLink.setIconRightCSS("o_icon o_icon_start");
		
		myEntriesLink = LinkFactory.createLink("goto.my.pages", mainVC, this);
		myEntriesLink.setIconRightCSS("o_icon o_icon_start");
		
		mySharedItemsLink = LinkFactory.createLink("goto.my.shared.items", mainVC, this);
		mySharedItemsLink.setIconRightCSS("o_icon o_icon_start");

		sharedItemsLink = LinkFactory.createLink("goto.shared.with.me", mainVC, this);
		sharedItemsLink.setIconRightCSS("o_icon o_icon_start");
		
		mediaCenterLink = LinkFactory.createLink("goto.media.center", mainVC, this);
		mediaCenterLink.setIconRightCSS("o_icon o_icon_start");
		
		editLastEntryLink = LinkFactory.createLink("edit.last.entry", mainVC, this);
		editLastEntryLink.setIconRightCSS("o_icon o_icon_start");
		createNewEntryLink = LinkFactory.createLink("new.entry", mainVC, this);
		createNewEntryLink.setIconRightCSS("o_icon o_icon_start");
		editLastUsedBinderLink = LinkFactory.createLink("edit.last.binder", mainVC, this);
		editLastUsedBinderLink.setIconRightCSS("o_icon o_icon_start");
		goToTrashLink = LinkFactory.createLink("go.to.trash", mainVC, this);
		goToTrashLink.setIconRightCSS("o_icon o_icon_start");

		putInitialPanel(mainVC);
	}
	
	@Override
	protected void doDispose() {
		//
	}

	@Override
	protected void event(UserRequest ureq, Component source, Event event) {
		if(myBindersLink == source) {
			doOpenMyBinders(ureq);
		} else if(myEntriesLink == source) {
			doOpenMyPages(ureq);
		} else if(mySharedItemsLink == source) {
			doOpenMySharedItems(ureq);
		} else if(sharedItemsLink == source) {
			doOpenSharedWithMe(ureq);
		} else if(mediaCenterLink == source) {
			doOpenMediaCenter(ureq);
		} else if(editLastEntryLink == source) {
			doOpenLastEntry(ureq);
		} else if(createNewEntryLink == source) {
			doNewEntry(ureq);
		} else if(editLastUsedBinderLink == source) {
			doOpenLastEditedBindersEntry(ureq);
		} else if(goToTrashLink == source) {
			showWarning("not.implemented");
		}
	}
	
	@Override
	public void activate(UserRequest ureq, List<ContextEntry> entries, StateEntry state) {
		if(entries == null || entries.isEmpty()) return;

		List<ContextEntry> subEntries = entries.subList(1, entries.size());
		String resName = entries.get(0).getOLATResourceable().getResourceableTypeName();
		if("Binder".equalsIgnoreCase(resName)) {
			BinderRef binder = new BinderRefImpl(entries.get(0).getOLATResourceable().getResourceableId());
			if(portfolioService.isMember(binder, getIdentity(), PortfolioRoles.owner.name())) {
				doOpenMyBinders(ureq).activate(ureq, entries, entries.get(0).getTransientState());
			} else {
				doOpenSharedWithMe(ureq).activate(ureq, entries, entries.get(0).getTransientState());
			}
		} else if("MyBinders".equalsIgnoreCase(resName)) {
			doOpenMyBinders(ureq).activate(ureq, subEntries, entries.get(0).getTransientState());
		} else if("MediaCenter".equalsIgnoreCase(resName)) {
			doOpenMediaCenter(ureq).activate(ureq, subEntries, entries.get(0).getTransientState());
		} else if("SharedWithMe".equalsIgnoreCase(resName)) {
			doOpenSharedWithMe(ureq).activate(ureq, subEntries, entries.get(0).getTransientState());
		} else if("MySharedItems".equalsIgnoreCase(resName)) {
			doOpenMySharedItems(ureq).activate(ureq, subEntries, entries.get(0).getTransientState());
		} else if("MyPages".equalsIgnoreCase(resName)) {
			doOpenMyPages(ureq).activate(ureq, subEntries, entries.get(0).getTransientState());
		}
	}
	
	private MediaCenterController doOpenMediaCenter(UserRequest ureq) {
		removeAsListenerAndDispose(mediaCenterCtrl);
		
		OLATResourceable pagesOres = OresHelper.createOLATResourceableInstance("MediaCenter", 0l);
		WindowControl swControl = addToHistory(ureq, pagesOres, null);
		mediaCenterCtrl = new MediaCenterController(ureq, swControl, stackPanel);
		listenTo(mediaCenterCtrl);
		stackPanel.pushController(translate("media.center"), mediaCenterCtrl);
		return mediaCenterCtrl;
	}
	
	private SharedItemsController doOpenSharedWithMe(UserRequest ureq) {
		removeAsListenerAndDispose(sharedWithMeCtrl);
		stackPanel.popUpToRootController(ureq);
		
		OLATResourceable pagesOres = OresHelper.createOLATResourceableInstance("SharedWithMe", 0l);
		WindowControl swControl = addToHistory(ureq, pagesOres, null);
		sharedWithMeCtrl = new SharedItemsController(ureq, swControl, stackPanel);
		listenTo(sharedWithMeCtrl);
		stackPanel.pushController(translate("shared.with.me"), sharedWithMeCtrl);
		return sharedWithMeCtrl;
	}
	
	private MySharedItemsController doOpenMySharedItems(UserRequest ureq) {
		removeAsListenerAndDispose(mySharedItemsCtrl);
		
		OLATResourceable pagesOres = OresHelper.createOLATResourceableInstance("MySharedItems", 0l);
		WindowControl swControl = addToHistory(ureq, pagesOres, null);
		mySharedItemsCtrl = new MySharedItemsController(ureq, swControl, stackPanel);
		listenTo(mySharedItemsCtrl);
		stackPanel.pushController(translate("my.shared.items"), mySharedItemsCtrl);
		return mySharedItemsCtrl;
	}
	
	private MyPageListController doOpenMyPages(UserRequest ureq) {
		removeAsListenerAndDispose(myPageListCtrl);
		
		OLATResourceable pagesOres = OresHelper.createOLATResourceableInstance("MyPages", 0l);
		WindowControl swControl = addToHistory(ureq, pagesOres, null);
		//owners of all pages
		BinderSecurityCallback secCallback = BinderSecurityCallbackFactory.getCallbackForMyPageList();
		myPageListCtrl = new MyPageListController(ureq, swControl, stackPanel, secCallback);
		listenTo(myPageListCtrl);
		stackPanel.pushController(translate("my.portfolio.pages.breadcrump"), myPageListCtrl);
		return myPageListCtrl;
	}
	
	private void doOpenLastEntry(UserRequest ureq) {
		Page lastModifiedPage = portfolioService.getLastPage(getIdentity(), false);
		if(lastModifiedPage == null) {
			//show message
		} else if(lastModifiedPage.getSection() == null) {
			MyPageListController ctrl = doOpenMyPages(ureq);
			ctrl.doOpenPage(ureq, lastModifiedPage);
		} else {
			Binder binder = lastModifiedPage.getSection().getBinder();
			List<ContextEntry> entries = new ArrayList<>();
			entries.add(BusinessControlFactory.getInstance().createContextEntry(OresHelper.createOLATResourceableInstance(Binder.class, binder.getKey())));
			entries.add(BusinessControlFactory.getInstance().createContextEntry(OresHelper.createOLATResourceableInstance(Page.class, lastModifiedPage.getKey())));
			BinderListController ctrl = doOpenMyBinders(ureq);
			ctrl.activate(ureq, entries, null);
		}
	}
	
	private BinderListController doOpenMyBinders(UserRequest ureq) {
		removeAsListenerAndDispose(myPortfolioListCtrl);
		stackPanel.popUpToRootController(ureq);
		
		OLATResourceable bindersOres = OresHelper.createOLATResourceableInstance("MyBinders", 0l);
		WindowControl swControl = addToHistory(ureq, bindersOres, null);
		myPortfolioListCtrl = new BinderListController(ureq, swControl, stackPanel);
		listenTo(myPortfolioListCtrl);
		stackPanel.pushController(translate("my.portfolio.binders.breadcrump"), myPortfolioListCtrl);
		return myPortfolioListCtrl;
	}
	
	private void doOpenLastEditedBindersEntry(UserRequest ureq) {
		Page lastModifiedPage = portfolioService.getLastPage(getIdentity(), true);
		if(lastModifiedPage == null) {
			//show message
		} else {
			Binder binder = lastModifiedPage.getSection().getBinder();
			List<ContextEntry> entries = new ArrayList<>();
			entries.add(BusinessControlFactory.getInstance().createContextEntry(OresHelper.createOLATResourceableInstance(Binder.class, binder.getKey())));
			BinderListController ctrl = doOpenMyBinders(ureq);
			ctrl.activate(ureq, entries, null);
		}
	}

	private void doNewEntry(UserRequest ureq) {
		MyPageListController ctrl = doOpenMyPages(ureq);
		ctrl.doCreateNewPage(ureq);
	}
}
