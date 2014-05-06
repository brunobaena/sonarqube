/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.rule2;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rule.Severity;
import org.sonar.api.utils.DateUtils;
import org.sonar.check.Cardinality;
import org.sonar.core.persistence.DbSession;
import org.sonar.core.persistence.MyBatis;
import org.sonar.core.qualityprofile.db.ActiveRuleDto;
import org.sonar.core.qualityprofile.db.ActiveRuleParamDto;
import org.sonar.core.qualityprofile.db.QualityProfileDao;
import org.sonar.core.qualityprofile.db.QualityProfileDto;
import org.sonar.core.rule.RuleDto;
import org.sonar.core.rule.RuleParamDto;
import org.sonar.server.tester.ServerTester;

import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

public class ActiveRuleIndexMediumTest {

  @ClassRule
  public static ServerTester tester = new ServerTester();

  RuleDao dao = tester.get(RuleDao.class);

  QualityProfileDao qualityProfileDao = tester.get(QualityProfileDao.class);

  RuleIndex index = tester.get(RuleIndex.class);

  @Before
  public void clear_data_store() {
    tester.clearDataStores();
  }

  @Test
  public void insert_and_index_activeRules() throws InterruptedException {
    DbSession dbSession = tester.get(MyBatis.class).openSession(false);
    ActiveRuleDao activeRuleDao = tester.get(ActiveRuleDao.class);

    QualityProfileDto profileDto = new QualityProfileDto()
      .setName("myprofile")
      .setLanguage("java");
    qualityProfileDao.insert(profileDto, dbSession);

    // insert db
    RuleKey ruleKey = RuleKey.of("javascript", "S001");
    RuleDto ruleDto = newRuleDto(ruleKey);
    dao.insert(ruleDto, dbSession);
    dbSession.commit();

    ActiveRuleDto activeRule = new ActiveRuleDto()
      .setInheritance("inherited")
      .setProfileId(profileDto.getId())
      .setRuleId(ruleDto.getId())
      .setSeverity(Severity.BLOCKER);

    activeRuleDao.insert(activeRule, dbSession);
    dbSession.commit();

    dbSession.close();

    // verify that activeRules are persisted in db
    List<ActiveRuleDto> persistedDtos = activeRuleDao.selectByRuleId(ruleDto.getId());
    assertThat(persistedDtos).hasSize(1);

    // verify that activeRules are indexed in es
    index.refresh();


    Rule hit = index.getByKey(ruleKey);
    assertThat(hit).isNotNull();
//    assertThat(hit.getField(RuleNormalizer.RuleField.ACTIVE.key())).isNotNull();
//
//    Map<String, Object> activeRules = (Map<String, Object>) hit.getField(RuleNormalizer.RuleField.ACTIVE.key());
//    assertThat(activeRules).hasSize(1);
  }

  @Test
  public void insert_and_index_activeRuleParams() throws InterruptedException {
    DbSession dbSession = tester.get(MyBatis.class).openSession(false);
    ActiveRuleDao activeRuleDao = tester.get(ActiveRuleDao.class);

    QualityProfileDto profileDto = new QualityProfileDto()
      .setName("myprofile")
      .setLanguage("java");
    qualityProfileDao.insert(profileDto, dbSession);

    // insert db
    RuleKey ruleKey = RuleKey.of("javascript", "S001");
    RuleDto ruleDto = newRuleDto(ruleKey);
    dao.insert(ruleDto, dbSession);

    RuleParamDto minParam = new RuleParamDto()
      .setRuleId(ruleDto.getId())
      .setName("min")
      .setType("STRING");
    dao.insert(minParam, dbSession);

    RuleParamDto maxParam = new RuleParamDto()
      .setRuleId(ruleDto.getId())
      .setName("max")
      .setType("STRING");
    dao.insert(maxParam, dbSession);


    ActiveRuleDto activeRule = new ActiveRuleDto()
      .setInheritance("inherited")
      .setProfileId(profileDto.getId())
      .setRuleId(ruleDto.getId())
      .setSeverity(Severity.BLOCKER);
    activeRuleDao.insert(activeRule, dbSession);

    ActiveRuleParamDto activeRuleMinParam = new ActiveRuleParamDto()
      .setActiveRuleId(activeRule.getId())
      .setKey(minParam.getName())
      .setValue("minimum")
      .setRulesParameterId(minParam.getId());
    activeRuleDao.insert(activeRuleMinParam, dbSession);

    ActiveRuleParamDto activeRuleMaxParam = new ActiveRuleParamDto()
      .setActiveRuleId(activeRule.getId())
      .setKey(maxParam.getName())
      .setValue("maximum")
      .setRulesParameterId(maxParam.getId());
    activeRuleDao.insert(activeRuleMaxParam, dbSession);

    dbSession.commit();
    dbSession.close();

    // verify that activeRulesParams are persisted in db
    List<ActiveRuleParamDto> persistedDtos = activeRuleDao.selectParamsByActiveRuleId(activeRule.getId());
    assertThat(persistedDtos).hasSize(2);

    // verify that activeRulesParams are indexed in es
    index.refresh();

//    Hit hit = index.getByKey(ruleKey);
//    assertThat(hit).isNotNull();
//
//    index.search(new RuleQuery(), new QueryOptions());
//
//    Map<String, Map> _activeRules = (Map<String, Map>) hit.getField(RuleNormalizer.RuleField.ACTIVE.key());
//    assertThat(_activeRules).isNotNull().hasSize(1);
//
//    Map<String, Object> _activeRule = (Map<String, Object>) Iterables.getFirst(_activeRules.values(),null);
//    assertThat(_activeRule.get(RuleNormalizer.RuleField.SEVERITY.key())).isEqualTo(Severity.BLOCKER);
//
//    Map<String, Map> _activeRuleParams = (Map<String, Map>) _activeRule.get(RuleNormalizer.RuleField.PARAMS.key());
//    assertThat(_activeRuleParams).isNotNull().hasSize(2);
//
//    Map<String, String> _activeRuleParamValue = (Map<String, String>) _activeRuleParams.get(maxParam.getName());
//    assertThat(_activeRuleParamValue).isNotNull().hasSize(1);
//    assertThat(_activeRuleParamValue.get(ActiveRuleNormalizer.ActiveRuleParamField.VALUE.key())).isEqualTo("maximum");

  }

  //TODO test delete, update, tags, params


  private RuleDto newRuleDto(RuleKey ruleKey) {
    return new RuleDto()
      .setRuleKey(ruleKey.rule())
      .setRepositoryKey(ruleKey.repository())
      .setName("Rule " + ruleKey.rule())
      .setDescription("Description " + ruleKey.rule())
      .setStatus(RuleStatus.READY.toString())
      .setConfigKey("InternalKey" + ruleKey.rule())
      .setSeverity(Severity.INFO)
      .setCardinality(Cardinality.SINGLE)
      .setLanguage("js")
      .setRemediationFunction("linear")
      .setDefaultRemediationFunction("linear_offset")
      .setRemediationCoefficient("1h")
      .setDefaultRemediationCoefficient("5d")
      .setRemediationOffset("5min")
      .setDefaultRemediationOffset("10h")
      .setEffortToFixDescription(ruleKey.repository() + "." + ruleKey.rule() + ".effortToFix")
      .setCreatedAt(DateUtils.parseDate("2013-12-16"))
      .setUpdatedAt(DateUtils.parseDate("2013-12-17"));
  }
}
