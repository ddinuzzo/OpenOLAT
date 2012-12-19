/**
* OLAT - Online Learning and Training<br>
* http://www.olat.org
* <p>
* Licensed under the Apache License, Version 2.0 (the "License"); <br>
* you may not use this file except in compliance with the License.<br>
* You may obtain a copy of the License at
* <p>
* http://www.apache.org/licenses/LICENSE-2.0
* <p>
* Unless required by applicable law or agreed to in writing,<br>
* software distributed under the License is distributed on an "AS IS" BASIS, <br>
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. <br>
* See the License for the specific language governing permissions and <br>
* limitations under the License.
* <p>
* Copyright (c) since 2004 at Multimedia- & E-Learning Services (MELS),<br>
* University of Zurich, Switzerland.
* <hr>
* <a href="http://www.openolat.org">
* OpenOLAT - Online Learning and Training</a><br>
* This file has been modified by the OpenOLAT community. Changes are licensed
* under the Apache 2.0 license as the original file.
*/

package org.olat.course.nodes.scorm;

import java.io.File;
import java.util.Properties;

import org.olat.core.gui.UserRequest;
import org.olat.core.gui.components.Component;
import org.olat.core.gui.components.Window;
import org.olat.core.gui.components.panel.Panel;
import org.olat.core.gui.components.tree.TreeEvent;
import org.olat.core.gui.components.velocity.VelocityContainer;
import org.olat.core.gui.control.ConfigurationChangedListener;
import org.olat.core.gui.control.Controller;
import org.olat.core.gui.control.ControllerEventListener;
import org.olat.core.gui.control.Event;
import org.olat.core.gui.control.WindowControl;
import org.olat.core.gui.control.controller.BasicController;
import org.olat.core.logging.AssertException;
import org.olat.core.util.CodeHelper;
import org.olat.core.util.event.GenericEventListener;
import org.olat.course.assessment.AssessmentHelper;
import org.olat.course.editor.NodeEditController;
import org.olat.course.nodes.ObjectivesHelper;
import org.olat.course.nodes.ScormCourseNode;
import org.olat.course.run.scoring.ScoreEvaluation;
import org.olat.course.run.userview.UserCourseEnvironment;
import org.olat.fileresource.FileResourceManager;
import org.olat.instantMessaging.CloseInstantMessagingEvent;
import org.olat.instantMessaging.InstantMessaging;
import org.olat.modules.ModuleConfiguration;
import org.olat.modules.scorm.ScormAPICallback;
import org.olat.modules.scorm.ScormAPIandDisplayController;
import org.olat.modules.scorm.ScormCPManifestTreeModel;
import org.olat.modules.scorm.ScormConstants;
import org.olat.modules.scorm.ScormMainManager;
import org.olat.repository.RepositoryEntry;
import org.olat.util.logging.activity.LoggingResourceable;

/**
 * Description: <BR/>
 * Run controller for content packaging course nodes
 * <P/>
 * 
 * @author Felix Jost
 */
public class ScormRunController extends BasicController implements ScormAPICallback, GenericEventListener, ConfigurationChangedListener {

	private ModuleConfiguration config;
	private File cpRoot;
	private Panel main;
	private VelocityContainer startPage;

	// private Translator translator;
	private ScormAPIandDisplayController scormDispC;
	private ScormCourseNode scormNode;

	// for external menu representation
	private ScormCPManifestTreeModel treeModel;
	private ControllerEventListener treeNodeClickListener;
	private UserCourseEnvironment userCourseEnv;
	private ChooseScormRunModeForm chooseScormRunMode;
	private boolean isPreview;
	private boolean isAssessable;
	private String assessableType;

	/**
	 * Use this constructor to launch a CP via Repository reference key set in
	 * the ModuleConfiguration. On the into page a title and the learning
	 * objectives can be placed.
	 * 
	 * @param config
	 * @param ureq
	 * @param userCourseEnv
	 * @param wControl
	 * @param cpNode
	 */
	public ScormRunController(ModuleConfiguration config, UserRequest ureq, UserCourseEnvironment userCourseEnv, WindowControl wControl,
			ScormCourseNode scormNode, boolean isPreview) {
		super(ureq, wControl);
		// assertion to make sure the moduleconfig is valid
		if (!ScormEditController.isModuleConfigValid(config))
			throw new AssertException("scorm run controller had an invalid module config:" + config.toString());
		this.isPreview = isPreview;
		this.userCourseEnv = userCourseEnv;
		this.config = config;
		this.scormNode = scormNode;

		addLoggingResourceable(LoggingResourceable.wrap(scormNode));
		init(ureq);
	}

	private void init(UserRequest ureq) {
		startPage = createVelocityContainer("run");
		// show browse mode option only if not assessable, hide it if in
		// "real test mode"
		isAssessable = config.getBooleanSafe(ScormEditController.CONFIG_ISASSESSABLE, true);
		if(isAssessable) {
			assessableType = config.getStringValue(ScormEditController.CONFIG_ASSESSABLE_TYPE,
					ScormEditController.CONFIG_ASSESSABLE_TYPE_SCORE);
		}

		// <OLATCE-289>
		// attemptsDependOnScore means that attempts are only incremented when a
		// score was given back by the SCORM
		// set start button if max attempts are not reached
		if (!maxAttemptsReached()) {
			chooseScormRunMode = new ChooseScormRunModeForm(ureq, getWindowControl(), !isAssessable);
			listenTo(chooseScormRunMode);
			startPage.put("chooseScormRunMode", chooseScormRunMode.getInitialComponent());
			startPage.contextPut("maxAttemptsReached", Boolean.FALSE);
		} else {
			startPage.contextPut("maxAttemptsReached", Boolean.TRUE);
		}
		// </OLATCE-289>

		main = new Panel("scormrunmain");
		doStartPage();
		putInitialPanel(main);

		boolean doSkip = config.getBooleanSafe(ScormEditController.CONFIG_SKIPLAUNCHPAGE, true);
		if (doSkip && !maxAttemptsReached()) {
			doLaunch(ureq, true);
			getWindowControl().getWindowBackOffice().addCycleListener(this);
		}
	}

	// <OLATCE-289>
	/**
	 * @return true if attempts of the user are equal to the maximum number of
	 *         attempts.
	 */
	private boolean maxAttemptsReached() {
		int maxAttempts = config.getIntegerSafe(ScormEditController.CONFIG_MAXATTEMPTS, 0);
		boolean maxAttemptsReached = false;
		if (maxAttempts > 0) {
			if (scormNode.getUserAttempts(userCourseEnv) >= maxAttempts) {
				maxAttemptsReached = true;
			}
		}
		return maxAttemptsReached;
	}

	// </OLATCE-289>

	/**
	 * @see org.olat.core.gui.control.DefaultController#event(org.olat.core.gui.UserRequest,
	 *      org.olat.core.gui.components.Component,
	 *      org.olat.core.gui.control.Event)
	 */
	public void event(UserRequest ureq, Component source, Event event) {
		//
	}

	/**
	 * @see org.olat.core.gui.control.DefaultController#event(org.olat.core.gui.UserRequest,
	 *      org.olat.core.gui.control.Controller,
	 *      org.olat.core.gui.control.Event)
	 */
	public void event(UserRequest ureq, Controller source, Event event) {
		if (source == scormDispC) { // just pass on the event.
			// <OLATCE-289>
			if (event.equals(Event.BACK_EVENT)) {
				if (maxAttemptsReached()) {
					startPage.contextPut("maxAttemptsReached", Boolean.TRUE);
				}
				doStartPage();
			} else {
				// </OLATCE-289>
				doStartPage();
				fireEvent(ureq, event);
			}
		} else if (source == null) { // external source
			if (event instanceof TreeEvent) {
				scormDispC.switchToPage((TreeEvent) event);
			}
		} else if (source == chooseScormRunMode) {
			doLaunch(ureq, true);
			scormDispC.activate();
		}
	}

	private void doStartPage() {

		// push title and learning objectives, only visible on intro page
		startPage.contextPut("menuTitle", scormNode.getShortTitle());
		startPage.contextPut("displayTitle", scormNode.getLongTitle());

		// Adding learning objectives
		String learningObj = scormNode.getLearningObjectives();
		if (learningObj != null) {
			Component learningObjectives = ObjectivesHelper.createLearningObjectivesComponent(learningObj, getLocale());
			startPage.put("learningObjectives", learningObjectives);
			startPage.contextPut("hasObjectives", Boolean.TRUE);
		} else {
			startPage.contextPut("hasObjectives", Boolean.FALSE);
		}

		if (isAssessable) {
			ScoreEvaluation scoreEval = scormNode.getUserScoreEvaluation(userCourseEnv);
			Float score = scoreEval.getScore();
			if(ScormEditController.CONFIG_ASSESSABLE_TYPE_SCORE.equals(assessableType)) {
				startPage.contextPut("score", score != null ? AssessmentHelper.getRoundedScore(score) : "0");
			}
			startPage.contextPut("hasPassedValue", (scoreEval.getPassed() == null ? Boolean.FALSE : Boolean.TRUE));
			startPage.contextPut("passed", scoreEval.getPassed());
			startPage.contextPut("comment", scormNode.getUserUserComment(userCourseEnv));
			startPage.contextPut("attempts", scormNode.getUserAttempts(userCourseEnv));
		}
		startPage.contextPut("isassessable", Boolean.valueOf(isAssessable));
		main.setContent(startPage);
	}

	private void doLaunch(UserRequest ureq, boolean doActivate) {
		ureq.getUserSession().getSingleUserEventCenter()
			.fireEventToListenersOf(new CloseInstantMessagingEvent(), InstantMessaging.TOWER_EVENT_ORES);

		if (cpRoot == null) {
			// it is the first time we start the contentpackaging from this
			// instance
			// of this controller.
			// need to be strict when launching -> "true"
			RepositoryEntry re = ScormEditController.getScormCPReference(config, true);
			if (re == null)
				throw new AssertException("configurationkey 'CONFIG_KEY_REPOSITORY_SOFTKEY' of BB CP was missing");
			cpRoot = FileResourceManager.getInstance().unzipFileResource(re.getOlatResource());
			addLoggingResourceable(LoggingResourceable.wrapScormRepositoryEntry(re));
			// should always exist because references cannot be deleted as long
			// as
			// nodes reference them
			if (cpRoot == null)
				throw new AssertException("file of repository entry " + re.getKey() + " was missing");
		}
		// else cpRoot is already set (save some db access if the user opens /
		// closes / reopens the cp from the same CPRuncontroller instance)

		String courseId;
		boolean showMenu = config.getBooleanSafe(ScormEditController.CONFIG_SHOWMENU, true);
		// fxdiff FXOLAT-116: SCORM improvements
		final boolean fullWindow = config.getBooleanSafe(ScormEditController.CONFIG_FULLWINDOW, true);

		if (isPreview) {
			courseId = new Long(CodeHelper.getRAMUniqueID()).toString();
			scormDispC = ScormMainManager.getInstance().createScormAPIandDisplayController(ureq, getWindowControl(), showMenu, null,
					cpRoot, null, courseId, ScormConstants.SCORM_MODE_BROWSE, ScormConstants.SCORM_MODE_NOCREDIT, true, null, doActivate,
					fullWindow, false);
		} else {
			boolean attemptsIncremented = false;
			//increment user attempts only once!
			if(!config.getBooleanSafe(ScormEditController.CONFIG_ADVANCESCORE, true)
					|| !config.getBooleanSafe(ScormEditController.CONFIG_ATTEMPTSDEPENDONSCORE, false)) {
				scormNode.incrementUserAttempts(userCourseEnv);
				attemptsIncremented = true;
			}
			
			courseId = userCourseEnv.getCourseEnvironment().getCourseResourceableId().toString();

			if (isAssessable) {
				scormDispC = ScormMainManager.getInstance().createScormAPIandDisplayController(ureq, getWindowControl(), showMenu, this,
						cpRoot, null, courseId + "-" + scormNode.getIdent(), ScormConstants.SCORM_MODE_NORMAL,
						ScormConstants.SCORM_MODE_CREDIT, false, assessableType, doActivate, fullWindow, attemptsIncremented);
				// <OLATCE-289>
				// scormNode.incrementUserAttempts(userCourseEnv);
				// </OLATCE-289>
			} else if (chooseScormRunMode.getSelectedElement().equals(ScormConstants.SCORM_MODE_NORMAL)) {
				scormDispC = ScormMainManager.getInstance().createScormAPIandDisplayController(ureq, getWindowControl(), showMenu, null,
						cpRoot, null, courseId + "-" + scormNode.getIdent(), ScormConstants.SCORM_MODE_NORMAL,
						ScormConstants.SCORM_MODE_CREDIT, false, assessableType, doActivate, fullWindow, attemptsIncremented);
			} else {
				scormDispC = ScormMainManager.getInstance().createScormAPIandDisplayController(ureq, getWindowControl(), showMenu, null,
						cpRoot, null, courseId, ScormConstants.SCORM_MODE_BROWSE, ScormConstants.SCORM_MODE_NOCREDIT, false, assessableType, doActivate,
						fullWindow, attemptsIncremented);
			}
			
		}
		// configure some display options
		boolean showNavButtons = config.getBooleanSafe(ScormEditController.CONFIG_SHOWNAVBUTTONS, true);
		scormDispC.showNavButtons(showNavButtons);
		boolean rawContent = config.getBooleanSafe(ScormEditController.CONFIG_RAW_CONTENT, false);
		scormDispC.setRawContent(rawContent);
		String height = (String) config.get(ScormEditController.CONFIG_HEIGHT);
		if (!height.equals(ScormEditController.CONFIG_HEIGHT_AUTO)) {
			scormDispC.setHeightPX(Integer.parseInt(height));
		}
		String contentEncoding = (String) config.get(NodeEditController.CONFIG_CONTENT_ENCODING);
		if (!contentEncoding.equals(NodeEditController.CONFIG_CONTENT_ENCODING_AUTO)) {
			scormDispC.setContentEncoding(contentEncoding);
		}
		String jsEncoding = (String) config.get(NodeEditController.CONFIG_JS_ENCODING);
		if (!jsEncoding.equals(NodeEditController.CONFIG_JS_ENCODING_AUTO)) {
			scormDispC.setJSEncoding(jsEncoding);
		}
		listenTo(scormDispC);
		// the scormDispC activates itself
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.olat.modules.scorm.ScormAPICallback#lmsCommit(java.lang.String,
	 * java.util.Properties)
	 */
	public void lmsCommit(String olatSahsId, Properties scoreProp, Properties lessonStatusProp) {
		//
	}

	// <BPS-620>
	/**
	 * @see org.olat.modules.scorm.ScormAPICallback#lmsFinish(java.lang.String,
	 *      java.util.Properties)
	 */
	public void lmsFinish(String olatSahsId, Properties scoreProp, Properties lessonStatusProp) {
		if (config.getBooleanSafe(ScormEditController.CONFIG_CLOSE_ON_FINISH, false)) {
			doStartPage();
			scormDispC.close();
		}
	}

	// </BPS-620>

	/**
	 * @return true if there is a treemodel and an event listener ready to be
	 *         used in outside this controller
	 */
	public boolean isExternalMenuConfigured() {
		return (config.getBooleanEntry(NodeEditController.CONFIG_COMPONENT_MENU).booleanValue());
	}

	@Override
	public void configurationChanged() {
		scormDispC.configurationChanged();
	}

	/**
	 * @see org.olat.core.gui.control.DefaultController#doDispose(boolean)
	 */
	protected void doDispose() {
		//
	}

	/**
	 * @return the treemodel of the enclosed ScormDisplayController, or null, if
	 *         no tree should be displayed (configured by author, see
	 *         DisplayConfigurationForm.CONFIG_COMPONENT_MENU)
	 */
	public ScormCPManifestTreeModel getTreeModel() {
		return treeModel;
	}

	/**
	 * @return the listener to listen to clicks to the nodes of the treemodel
	 *         obtained calling getTreeModel()
	 */
	public ControllerEventListener getTreeNodeClickListener() {
		return treeNodeClickListener;
	}

	@Override
	public void event(Event event) {
		if (event == Window.END_OF_DISPATCH_CYCLE || event == Window.BEFORE_RENDER_ONLY) {
			// do initial modal dialog activation 
			// a) just after the dispatching of the event which is before
			// rendering after a normal click
			// b) just before a render-only operation which happens when using a
			// jump-in URL followed by a redirect without dispatching
			scormDispC.activate();
			getWindowControl().getWindowBackOffice().removeCycleListener(this);
		}
	}

}