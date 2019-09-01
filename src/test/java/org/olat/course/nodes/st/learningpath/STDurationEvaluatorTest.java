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
package org.olat.course.nodes.st.learningpath;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.olat.course.learningpath.ui.LearningPathTreeNode;

/**
 * 
 * Initial date: 1 Sep 2019<br>
 * @author uhensler, urs.hensler@frentix.com, http://www.frentix.com
 *
 */
public class STDurationEvaluatorTest {
	
	STDurationEvaluator sut = new STDurationEvaluator();
	
	@Test
	public void shouldNotDependOnCurrentNode() {
		assertThat(sut.isDependingOnCurrentNode()).isFalse();
	}

	@Test
	public void shouldDependOnChildren() {
		assertThat(sut.isdependingOnChildNodes()).isTrue();
	}
	
	@Test
	public void shouldSumDurationOfChildren() {
		List<LearningPathTreeNode> children = new ArrayList<>();
		LearningPathTreeNode child1 = new LearningPathTreeNode(null, 1);
		child1.setDuration(Integer.valueOf(2));
		children.add(child1);
		LearningPathTreeNode child2 = new LearningPathTreeNode(null, 1);
		child2.setDuration(Integer.valueOf(3));
		children.add(child2);
		LearningPathTreeNode child3 = new LearningPathTreeNode(null, 1);
		child3.setDuration(null);
		children.add(child3);
		
		Integer duration = sut.getDuration(children);
		
		assertThat(duration).isEqualTo(5);
	}

}
