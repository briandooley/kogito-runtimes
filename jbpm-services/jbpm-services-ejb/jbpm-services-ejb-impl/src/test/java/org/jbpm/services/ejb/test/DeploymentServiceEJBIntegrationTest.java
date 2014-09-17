/*
 * Copyright 2014 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.services.ejb.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.kie.scanner.MavenRepository.getMavenRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ejb.EJB;

import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jbpm.kie.services.impl.KModuleDeploymentUnit;
import org.jbpm.services.api.model.DeployedUnit;
import org.jbpm.services.api.model.DeploymentUnit;
import org.jbpm.services.api.model.ProcessDefinition;
import org.jbpm.services.ejb.api.DeploymentServiceEJBLocal;
import org.jbpm.services.ejb.api.RuntimeDataServiceEJBLocal;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.task.model.TaskSummary;
import org.kie.internal.query.QueryContext;
import org.kie.internal.runtime.manager.context.EmptyContext;
import org.kie.scanner.MavenRepository;

@RunWith(Arquillian.class)
public class DeploymentServiceEJBIntegrationTest extends AbstractTestSupport {

	private List<DeploymentUnit> units = new ArrayList<DeploymentUnit>();
	
    @After
    public void cleanup() {

    	cleanupSingletonSessionId();
        if (units != null && !units.isEmpty()) {
            for (DeploymentUnit unit : units) {
                deploymentService.undeploy(unit);
            }
            units.clear();
        }
    }
	
	@Deployment
	public static WebArchive createDeployment() {
		File archive = new File("target/sample-war-ejb-app.war");
		if (!archive.exists()) {
			throw new IllegalStateException("There is no archive yet generated, run maven build or mvn assembly:assembly");
		}
		WebArchive war = ShrinkWrap.createFromZipFile(WebArchive.class, archive);
		war.addPackage("org.jbpm.services.ejb.test"); // test cases
		// deploy test kjar
		deployKjar();
		
		return war;
	}
	
	protected static void deployKjar() {
		KieServices ks = KieServices.Factory.get();
        ReleaseId releaseId = ks.newReleaseId(GROUP_ID, ARTIFACT_ID, VERSION);
        List<String> processes = new ArrayList<String>();
        processes.add("processes/customtask.bpmn");
        processes.add("processes/humanTask.bpmn");
        processes.add("processes/signal.bpmn");
        processes.add("processes/import.bpmn");
        processes.add("processes/callactivity.bpmn");
        
        InternalKieModule kJar1 = createKieJar(ks, releaseId, processes);
        File pom = new File("target/kmodule", "pom.xml");
        pom.getParentFile().mkdir();
        try {
            FileOutputStream fs = new FileOutputStream(pom);
            fs.write(getPom(releaseId).getBytes());
            fs.close();
        } catch (Exception e) {
            
        }
        MavenRepository repository = getMavenRepository();
        repository.deployArtifact(releaseId, kJar1, pom);
        
        ReleaseId releaseIdSupport = ks.newReleaseId(GROUP_ID, "support", VERSION);
        List<String> processesSupport = new ArrayList<String>();
        processesSupport.add("processes/support.bpmn");
        
        InternalKieModule kJar2 = createKieJar(ks, releaseIdSupport, processesSupport);
        File pom2 = new File("target/kmodule2", "pom.xml");
        pom2.getParentFile().mkdir();
        try {
            FileOutputStream fs = new FileOutputStream(pom2);
            fs.write(getPom(releaseIdSupport).getBytes());
            fs.close();
        } catch (Exception e) {
            
        }

        repository.deployArtifact(releaseIdSupport, kJar2, pom2);
	}
	



	@EJB
	private RuntimeDataServiceEJBLocal runtimeDataService;
	
	@EJB
	private DeploymentServiceEJBLocal deploymentService;
	
	@Test
    public void testDeploymentOfProcesses() {
        
        assertNotNull(deploymentService);
        
        DeploymentUnit deploymentUnit = new KModuleDeploymentUnit(GROUP_ID, ARTIFACT_ID, VERSION);
        
        deploymentService.deploy(deploymentUnit);
        units.add(deploymentUnit);
        
        DeployedUnit deployed = deploymentService.getDeployedUnit(deploymentUnit.getIdentifier());
        assertNotNull(deployed);
        assertNotNull(deployed.getDeploymentUnit());
        assertNotNull(deployed.getRuntimeManager());
        
        assertNotNull(runtimeDataService);
        Collection<ProcessDefinition> processes = runtimeDataService.getProcesses(new QueryContext());
        assertNotNull(processes);
        assertEquals(5, processes.size());
        
        processes = runtimeDataService.getProcessesByFilter("custom", new QueryContext());
        assertNotNull(processes);
        assertEquals(1, processes.size());
        
        processes = runtimeDataService.getProcessesByDeploymentId(deploymentUnit.getIdentifier(), new QueryContext());
        assertNotNull(processes);
        assertEquals(5, processes.size());
        
        ProcessDefinition process = runtimeDataService.getProcessById("customtask");
        assertNotNull(process);
        
        RuntimeManager manager = deploymentService.getRuntimeManager(deploymentUnit.getIdentifier());
        assertNotNull(manager);
        
        RuntimeEngine engine = manager.getRuntimeEngine(EmptyContext.get());
        assertNotNull(engine);
        
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", "test");
        ProcessInstance processInstance = engine.getKieSession().startProcess("customtask", params);
        
        assertEquals(ProcessInstance.STATE_COMPLETED, processInstance.getState());
        
    }
    
    
    @Test
    public void testDeploymentOfAllProcesses() {
        
        assertNotNull(deploymentService);
        // deploy first unit
        DeploymentUnit deploymentUnitGeneral = new KModuleDeploymentUnit(GROUP_ID, ARTIFACT_ID, VERSION);
        deploymentService.deploy(deploymentUnitGeneral);
        units.add(deploymentUnitGeneral);
        
        RuntimeManager managerGeneral = deploymentService.getRuntimeManager(deploymentUnitGeneral.getIdentifier());
        assertNotNull(managerGeneral);
        
        // deploy second unit
        DeploymentUnit deploymentUnitSupport = new KModuleDeploymentUnit(GROUP_ID, "support", VERSION);        
        deploymentService.deploy(deploymentUnitSupport);
        units.add(deploymentUnitSupport);
        
        DeployedUnit deployedGeneral = deploymentService.getDeployedUnit(deploymentUnitGeneral.getIdentifier());
        assertNotNull(deployedGeneral);
        assertNotNull(deployedGeneral.getDeploymentUnit());
        assertNotNull(deployedGeneral.getRuntimeManager());
        
        RuntimeManager managerSupport = deploymentService.getRuntimeManager(deploymentUnitSupport.getIdentifier());
        assertNotNull(managerSupport);
        
        DeployedUnit deployedSupport = deploymentService.getDeployedUnit(deploymentUnitSupport.getIdentifier());
        assertNotNull(deployedSupport);
        assertNotNull(deployedSupport.getDeploymentUnit());
        assertNotNull(deployedSupport.getRuntimeManager());
        
        // execute process that is bundled in first deployment unit
        RuntimeEngine engine = managerGeneral.getRuntimeEngine(EmptyContext.get());
        assertNotNull(engine);
        
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", "test");
        ProcessInstance processInstance = engine.getKieSession().startProcess("customtask", params);
        
        assertEquals(ProcessInstance.STATE_COMPLETED, processInstance.getState());
        
        // execute process that is in second deployment unit
        RuntimeEngine engineSupport = managerSupport.getRuntimeEngine(EmptyContext.get());
        assertNotNull(engineSupport);
        
        ProcessInstance supportPI = engineSupport.getKieSession().startProcess("support.process");
        assertEquals(ProcessInstance.STATE_ACTIVE, supportPI.getState());
        
        List<TaskSummary> tasks = engineSupport.getTaskService().getTasksAssignedAsPotentialOwner("salaboy", "en-UK");
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        
        engineSupport.getKieSession().abortProcessInstance(supportPI.getId());
        assertNull(engineSupport.getKieSession().getProcessInstance(supportPI.getState()));
    }
    
    @Test
    public void testDuplicatedDeployment() {
            
        assertNotNull(deploymentService);
        
        DeploymentUnit deploymentUnit = new KModuleDeploymentUnit(GROUP_ID, ARTIFACT_ID, VERSION);       
        deploymentService.deploy(deploymentUnit);
        units.add(deploymentUnit);
        DeployedUnit deployedGeneral = deploymentService.getDeployedUnit(deploymentUnit.getIdentifier());
        assertNotNull(deployedGeneral);
        assertNotNull(deployedGeneral.getDeploymentUnit());
        assertNotNull(deployedGeneral.getRuntimeManager());
        try {
	        // duplicated deployment of the same deployment unit should fail
	        deploymentService.deploy(deploymentUnit);
        } catch(Exception e) {
        	assertTrue(e.getMessage().endsWith("Unit with id org.jbpm.test:test-module:1.0.0-SNAPSHOT is already deployed"));
        }
    }
}
