/*
 * Copyright 2011 DTO Solutions, Inc. (http://dtosolutions.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
* TestScriptFileNodeStepExecutor.java
* 
* User: Greg Schueler <a href="mailto:greg@dtosolutions.com">greg@dtosolutions.com</a>
* Created: 3/24/11 3:37 PM
* 
*/
package com.dtolabs.rundeck.core.execution.workflow.steps.node.impl;

import com.dtolabs.rundeck.core.common.Framework;
import com.dtolabs.rundeck.core.common.FrameworkProject;
import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.common.NodeEntryImpl;
import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.ExecutionContextImpl;
import com.dtolabs.rundeck.core.execution.ExecutionException;
import com.dtolabs.rundeck.core.execution.ExecutionListener;
import com.dtolabs.rundeck.core.execution.service.*;
import com.dtolabs.rundeck.core.execution.workflow.StepExecutionContext;
import com.dtolabs.rundeck.core.execution.workflow.steps.node.NodeStepException;
import com.dtolabs.rundeck.core.execution.workflow.steps.node.NodeStepResult;
import com.dtolabs.rundeck.core.tools.AbstractBaseTest;
import com.dtolabs.rundeck.core.utils.FileUtils;
import com.dtolabs.rundeck.core.utils.NodeSet;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * TestScriptFileNodeStepExecutor is ...
 *
 * @author Greg Schueler <a href="mailto:greg@dtosolutions.com">greg@dtosolutions.com</a>
 */
public class TestScriptFileNodeStepExecutor extends AbstractBaseTest {
    private static final String PROJ_NAME = "TestScriptFileNodeStepExecutor";

    public TestScriptFileNodeStepExecutor(String name) {
        super(name);
    }

    public void setUp() {

        final Framework frameworkInstance = getFrameworkInstance();
        final FrameworkProject frameworkProject = frameworkInstance.getFrameworkProjectMgr().createFrameworkProject(
            PROJ_NAME);
        File resourcesfile = new File(frameworkProject.getNodesResourceFilePath());
        //copy test nodes to resources file
        try {
            FileUtils.copyFileStreams(new File("src/test/resources/com/dtolabs/rundeck/core/common/test-nodes1.xml"),
                resourcesfile);
        } catch (IOException e) {
            throw new RuntimeException("Caught Setup exception: " + e.getMessage(), e);
        }

    }

    public void tearDown() throws Exception {
        super.tearDown();
        File projectdir = new File(getFrameworkProjectsBase(), PROJ_NAME);
        FileUtils.deleteDir(projectdir);
    }

    public static class testFileCopier implements FileCopier {
        String testResult;
        ExecutionContext testContext;
        InputStream testInput;
        INodeEntry testNode;
        boolean throwException;

        public String copyFileStream(ExecutionContext context, InputStream input, INodeEntry node) throws
            FileCopierException {
            testContext = context;
            testNode = node;
            testInput = input;
            if(throwException) {
                throw new FileCopierException("copyFileStream test");
            }
            return testResult;
        }

        File testFile;

        public String copyFile(ExecutionContext context, File file, INodeEntry node) throws FileCopierException {
            testContext = context;
            testNode = node;
            testFile = file;
            if (throwException) {
                throw new FileCopierException("copyFile test");
            }
            return testResult;
        }

        String testScript;

        public String copyScriptContent(ExecutionContext context, String script, INodeEntry node) throws
            FileCopierException {
            testContext = context;
            testNode = node;
            testScript = script;

            if (throwException) {
                throw new FileCopierException("copyScriptContent test");
            }
            return testResult;
        }
    }

    public static class multiTestNodeExecutor implements NodeExecutor {
        List<ExecutionContext> testContext = new ArrayList<ExecutionContext>();
        List<String[]> testCommand = new ArrayList<String[]>();
        List<INodeEntry> testNode = new ArrayList<INodeEntry>();
        List<NodeExecutorResult> testResult = new ArrayList<NodeExecutorResult>();
        int index = 0;

        public NodeExecutorResult executeCommand(ExecutionContext context, String[] command, INodeEntry node) throws
            ExecutionException {
            this.testContext.add(context);
            this.testCommand.add(command);
            this.testNode.add(node);
            return testResult.get(index++);
        }

    }

    /**
     * Unix target node will copy using file copier, then exec "chmod +x [destfile]", then execute the
     * filepath
     */
    public void testInterpretCommandScriptContentLocalUnix() throws Exception {
        final Framework frameworkInstance = getFrameworkInstance();
        ScriptFileNodeStepExecutor interpret = new ScriptFileNodeStepExecutor(getFrameworkInstance());

        //setup nodeexecutor for local node
        multiTestNodeExecutor testexec = new multiTestNodeExecutor();
        NodeExecutorService service = NodeExecutorService.getInstanceForFramework(getFrameworkInstance());
        service.registerInstance("local", testexec);

        testFileCopier testcopier = new testFileCopier();
        FileCopierService copyservice = FileCopierService.getInstanceForFramework(getFrameworkInstance());
        copyservice.registerInstance("local", testcopier);

        //execute command interpreter on local node
        final NodeEntryImpl test1 = new NodeEntryImpl("testhost1", "test1");
        test1.setOsFamily("unix");
        final StepExecutionContext context = ExecutionContextImpl.builder()
            .frameworkProject(PROJ_NAME)
            .framework(frameworkInstance)
            .user("blah")
            .threadCount(1)
            .build();
        final String testScript = "a script";

        ScriptFileCommand command = new ScriptFileCommandBase()  {
            public String getScript() {
                return testScript;
            }
        };
        {
            final ArrayList<NodeExecutorResult> nodeExecutorResults = new ArrayList<NodeExecutorResult>();
            nodeExecutorResults.add(new NodeExecutorResultImpl(true, null, 1));
            nodeExecutorResults.add(new NodeExecutorResultImpl(true, null, 2));
            testexec.testResult=nodeExecutorResults;
            testcopier.testResult="/test/file/path";
            final NodeStepResult interpreterResult = interpret.executeNodeStep(context, command, test1);

            assertNotNull(interpreterResult);
            assertTrue(interpreterResult.isSuccess());
            assertEquals(interpreterResult, nodeExecutorResults.get(1));

            assertEquals(context, testcopier.testContext);
            assertEquals(testScript, testcopier.testScript);
            assertEquals(test1, testcopier.testNode);

            //test nodeexecutor was called twice
            assertEquals(2, testexec.index);
            //first call is chmod +x filepath
            final String[] strings = testexec.testCommand.get(0);
            assertEquals(3, strings.length);
            assertEquals("chmod", strings[0]);
            assertEquals("+x", strings[1]);
            assertEquals("/test/file/path", strings[2]);
//            assertEquals(context, testexec.testContext.get(0));
            assertEquals(test1, testexec.testNode.get(0));

            //second call is to exec the filepath
            final String[] strings2 = testexec.testCommand.get(1);
            assertEquals(1, strings2.length);
            assertEquals(strings2[0], strings[2]);
            assertEquals("/test/file/path", strings2[0]);
//            assertEquals(context, testexec.testContext.get(1));
            assertEquals(test1, testexec.testNode.get(1));
        }
    }

    /**
     * Unix target node will copy using file copier, then exec "chmod +x [destfile]", then execute the filepath
     */
    public void testInterpretCommandScriptContentWithArgs() throws Exception {
        final Framework frameworkInstance = getFrameworkInstance();
        ScriptFileNodeStepExecutor interpret = new ScriptFileNodeStepExecutor(getFrameworkInstance());

        //setup nodeexecutor for local node
        multiTestNodeExecutor testexec = new multiTestNodeExecutor();
        NodeExecutorService service = NodeExecutorService.getInstanceForFramework(getFrameworkInstance());
        service.registerInstance("local", testexec);

        testFileCopier testcopier = new testFileCopier();
        FileCopierService copyservice = FileCopierService.getInstanceForFramework(getFrameworkInstance());
        copyservice.registerInstance("local", testcopier);

        //execute command interpreter on local node
        final NodeEntryImpl test1 = new NodeEntryImpl("testhost1", "test1");
        test1.setOsFamily("unix");
        final StepExecutionContext context = ExecutionContextImpl.builder()
            .frameworkProject(PROJ_NAME)
            .framework(frameworkInstance)
            .user("blah")
            .build();
        final String testScript = "a script";

        ScriptFileCommand command = new ScriptFileCommandBase() {
            public String getScript() {
                return testScript;
            }

            public String[] getArgs() {
                return new String[]{"some","args"};
            }
        };
        {
            final ArrayList<NodeExecutorResult> nodeExecutorResults = new ArrayList<NodeExecutorResult>();
            nodeExecutorResults.add(new NodeExecutorResultImpl(true, null, 1));
            nodeExecutorResults.add(new NodeExecutorResultImpl(true, null, 2));
            testexec.testResult = nodeExecutorResults;
            testcopier.testResult = "/test/file/path";
            final NodeStepResult interpreterResult = interpret.executeNodeStep(context, command, test1);

            assertNotNull(interpreterResult);
            assertTrue(interpreterResult.isSuccess());
            assertEquals(interpreterResult, nodeExecutorResults.get(1));

            assertEquals(context, testcopier.testContext);
            assertEquals(testScript, testcopier.testScript);
            assertEquals(test1, testcopier.testNode);

            //test nodeexecutor was called twice
            assertEquals(2, testexec.index);
            //first call is chmod +x filepath
            final String[] strings = testexec.testCommand.get(0);
            assertEquals(3, strings.length);
            assertEquals("chmod", strings[0]);
            assertEquals("+x", strings[1]);
            assertEquals("/test/file/path", strings[2]);
//            assertEquals(context, testexec.testContext.get(0));
            assertEquals(test1, testexec.testNode.get(0));

            //second call is to exec the filepath
            final String[] strings2 = testexec.testCommand.get(1);
            assertEquals(3, strings2.length);
            assertEquals(strings2[0], strings[2]);
            assertEquals("/test/file/path", strings2[0]);
            assertEquals("some", strings2[1]);
            assertEquals("args", strings2[2]);
//            assertEquals(context, testexec.testContext.get(1));
            assertEquals(test1, testexec.testNode.get(1));
        }
    }

    /**
     * Unix target node will copy using file copier, then exec "chmod +x [destfile]", then execute the
     * filepath.
     *
     * test result if chmod fails.
     */
    public void testInterpretCommandScriptContentLocalUnixChmodFailure() throws Exception {
        final Framework frameworkInstance = getFrameworkInstance();
        ScriptFileNodeStepExecutor interpret = new ScriptFileNodeStepExecutor(frameworkInstance);

        //setup nodeexecutor for local node
        multiTestNodeExecutor testexec = new multiTestNodeExecutor();
        NodeExecutorService service = NodeExecutorService.getInstanceForFramework(getFrameworkInstance());
        service.registerInstance("local", testexec);

        testFileCopier testcopier = new testFileCopier();
        FileCopierService copyservice = FileCopierService.getInstanceForFramework(getFrameworkInstance());
        copyservice.registerInstance("local", testcopier);

        //execute command interpreter on local node
        final NodeEntryImpl test1 = new NodeEntryImpl("testhost1", "test1");
        test1.setOsFamily("unix");

        final StepExecutionContext context = ExecutionContextImpl.builder()
            .frameworkProject(PROJ_NAME)
            .framework(frameworkInstance)
            .user("blah")
            .build();
        final String testScript = "a script";

        ScriptFileCommand command = new ScriptFileCommandBase() {
            public String getScript() {
                return testScript;
            }

        };
        {
            final ArrayList<NodeExecutorResult> nodeExecutorResults = new ArrayList<NodeExecutorResult>();
            nodeExecutorResults.add(new NodeExecutorResultImpl(false, null, 1));
            testexec.testResult=nodeExecutorResults;
            testcopier.testResult = "/test/file/path";
            final NodeStepResult interpreterResult = interpret.executeNodeStep(context, command, test1);

            assertNotNull(interpreterResult);
            assertFalse(interpreterResult.isSuccess());
            assertEquals(interpreterResult, nodeExecutorResults.get(0));

            assertEquals(context, testcopier.testContext);
            assertEquals(testScript, testcopier.testScript);
            assertEquals(test1, testcopier.testNode);

            //test nodeexecutor was called once
            assertEquals(1, testexec.index);
            //first call is chmod +x filepath
            final String[] strings = testexec.testCommand.get(0);
            assertEquals(3, strings.length);
            assertEquals("chmod", strings[0]);
            assertEquals("+x", strings[1]);
            assertEquals("/test/file/path", strings[2]);
//            assertEquals(context, testexec.testContext.get(0));
            assertEquals(test1, testexec.testNode.get(0));


        }
    }

    /**
     * Windows target node will copy using file copier, then execute the
     * filepath
     */
    public void testInterpretCommandScriptContentLocalWindows() throws Exception {
        final Framework frameworkInstance = getFrameworkInstance();
        ScriptFileNodeStepExecutor interpret = new ScriptFileNodeStepExecutor(frameworkInstance);

        //setup nodeexecutor for local node
        multiTestNodeExecutor testexec = new multiTestNodeExecutor();
        NodeExecutorService service = NodeExecutorService.getInstanceForFramework(getFrameworkInstance());
        service.registerInstance("local", testexec);

        testFileCopier testcopier = new testFileCopier();
        FileCopierService copyservice = FileCopierService.getInstanceForFramework(getFrameworkInstance());
        copyservice.registerInstance("local", testcopier);

        //execute command interpreter on local node
        final NodeEntryImpl test1 = new NodeEntryImpl("testhost1", "test1");
        test1.setOsFamily("windows");

        final StepExecutionContext context = ExecutionContextImpl.builder()
            .frameworkProject(PROJ_NAME)
            .framework(frameworkInstance)
            .user("blah")
            .build();
        final String testScript = "a script";

        ScriptFileCommand command = new ScriptFileCommandBase() {
            public String getScript() {
                return testScript;
            }

        };
        {
            final ArrayList<NodeExecutorResult> nodeExecutorResults = new ArrayList<NodeExecutorResult>();
            nodeExecutorResults.add(new NodeExecutorResultImpl(true, null, 1));
            testexec.testResult=nodeExecutorResults;
            testcopier.testResult = "/test/file/path";
            final NodeStepResult interpreterResult = interpret.executeNodeStep(context, command, test1);

            assertNotNull(interpreterResult);
            assertTrue(interpreterResult.isSuccess());
            assertEquals(interpreterResult, nodeExecutorResults.get(0));

            assertEquals(context, testcopier.testContext);
            assertEquals(testScript, testcopier.testScript);
            assertEquals(test1, testcopier.testNode);

            //test nodeexecutor was called twice
            assertEquals(1, testexec.index);

            //second call is to exec the filepath
            final String[] strings2 = testexec.testCommand.get(0);
            assertEquals(1, strings2.length);
            assertNotNull(strings2[0]);
            assertEquals("/test/file/path", strings2[0]);
//            assertEquals(context, testexec.testContext.get(0));
            assertEquals(test1, testexec.testNode.get(0));
        }
    }

    /**
     * Use script file specifier in execution item
     */
    public void testInterpretCommandScriptFileLocal() throws Exception {
        final Framework frameworkInstance = getFrameworkInstance();
        ScriptFileNodeStepExecutor interpret = new ScriptFileNodeStepExecutor(frameworkInstance);

        //setup nodeexecutor for local node
        multiTestNodeExecutor testexec = new multiTestNodeExecutor();
        NodeExecutorService service = NodeExecutorService.getInstanceForFramework(getFrameworkInstance());
        service.registerInstance("local", testexec);

        testFileCopier testcopier = new testFileCopier();
        FileCopierService copyservice = FileCopierService.getInstanceForFramework(getFrameworkInstance());
        copyservice.registerInstance("local", testcopier);

        //execute command interpreter on local node
        final NodeEntryImpl test1 = new NodeEntryImpl("testhost1", "test1");
        test1.setOsFamily("unix");

        final StepExecutionContext context = ExecutionContextImpl.builder()
            .frameworkProject(PROJ_NAME)
            .framework(frameworkInstance)
            .user("blah")
            .build();
        final File testScriptFile = new File("Testfile");


        ScriptFileCommand command = new ScriptFileCommandBase() {

            public String getServerScriptFilePath() {
                return testScriptFile.getAbsolutePath();
            }

        };
        {
            final ArrayList<NodeExecutorResult> nodeExecutorResults = new ArrayList<NodeExecutorResult>();
            nodeExecutorResults.add(new NodeExecutorResultImpl(true, null, 1));
            nodeExecutorResults.add(new NodeExecutorResultImpl(true, null, 2));
            testexec.testResult = nodeExecutorResults;
            testcopier.testResult = "/test/file/path";
            final NodeStepResult interpreterResult = interpret.executeNodeStep(context, command, test1);

            assertNotNull(interpreterResult);
            assertTrue(interpreterResult.isSuccess());
            assertEquals(interpreterResult, nodeExecutorResults.get(1));

            assertEquals(context, testcopier.testContext);
            assertNull(testcopier.testScript);
            assertNull(testcopier.testInput);
            assertEquals(testScriptFile.getAbsolutePath(), testcopier.testFile.getAbsolutePath());
            assertEquals(test1, testcopier.testNode);

            //test nodeexecutor was called twice
            assertEquals(2, testexec.index);
            //first call is chmod +x filepath
            final String[] strings = testexec.testCommand.get(0);
            assertEquals(3, strings.length);
            assertEquals("chmod", strings[0]);
            assertEquals("+x", strings[1]);
            assertEquals("/test/file/path", strings[2]);
//            assertEquals(context, testexec.testContext.get(0));
            assertEquals(test1, testexec.testNode.get(0));

            //second call is to exec the filepath
            final String[] strings2 = testexec.testCommand.get(1);
            assertEquals(1, strings2.length);
            assertEquals(strings2[0], strings[2]);
            assertEquals("/test/file/path", strings2[0]);
//            assertEquals(context, testexec.testContext.get(1));
            assertEquals(test1, testexec.testNode.get(1));
        }
    }

    /**
     * Test inputstream
     */
    public void testInterpretCommandScriptInputLocal() throws Exception {
        final Framework frameworkInstance = getFrameworkInstance();
        ScriptFileNodeStepExecutor interpret = new ScriptFileNodeStepExecutor(frameworkInstance);

        //setup nodeexecutor for local node
        multiTestNodeExecutor testexec = new multiTestNodeExecutor();
        NodeExecutorService service = NodeExecutorService.getInstanceForFramework(getFrameworkInstance());
        service.registerInstance("local", testexec);

        testFileCopier testcopier = new testFileCopier();
        FileCopierService copyservice = FileCopierService.getInstanceForFramework(getFrameworkInstance());
        copyservice.registerInstance("local", testcopier);

        //execute command interpreter on local node
        final NodeEntryImpl test1 = new NodeEntryImpl("testhost1", "test1");
        test1.setOsFamily("unix");

        final StepExecutionContext context = ExecutionContextImpl.builder()
            .frameworkProject(PROJ_NAME)
            .framework(frameworkInstance)
            .user("blah")
            .build();
        final InputStream inputStream = new ByteArrayInputStream(new byte[]{0});


        ScriptFileCommand command = new ScriptFileCommandBase() {

            public InputStream getScriptAsStream() {
                return inputStream;
            }

        };
        {
            final ArrayList<NodeExecutorResult> nodeExecutorResults = new ArrayList<NodeExecutorResult>();
            nodeExecutorResults.add(new NodeExecutorResultImpl(true, null, 1));
            nodeExecutorResults.add(new NodeExecutorResultImpl(true, null, 2));
            testexec.testResult = nodeExecutorResults;
            testcopier.testResult = "/test/file/path";
            final NodeStepResult interpreterResult = interpret.executeNodeStep(context, command, test1);

            assertNotNull(interpreterResult);
            assertTrue(interpreterResult.isSuccess());
            assertEquals(interpreterResult, nodeExecutorResults.get(1));

            assertEquals(context, testcopier.testContext);
            assertNull(testcopier.testScript);
            assertEquals(inputStream, testcopier.testInput);
            assertNull(testcopier.testFile);
            assertEquals(test1, testcopier.testNode);

            //test nodeexecutor was called twice
            assertEquals(2, testexec.index);
            //first call is chmod +x filepath
            final String[] strings = testexec.testCommand.get(0);
            assertEquals(3, strings.length);
            assertEquals("chmod", strings[0]);
            assertEquals("+x", strings[1]);
            assertEquals("/test/file/path", strings[2]);
//            assertEquals(context, testexec.testContext.get(0));
            assertEquals(test1, testexec.testNode.get(0));

            //second call is to exec the filepath
            final String[] strings2 = testexec.testCommand.get(1);
            assertEquals(1, strings2.length);
            assertEquals(strings2[0], strings[2]);
            assertEquals("/test/file/path", strings2[0]);
//            assertEquals(context, testexec.testContext.get(1));
            assertEquals(test1, testexec.testNode.get(1));
        }
    }


    public void testInterpretCommandCopyFailure() throws Exception {
        final Framework frameworkInstance = getFrameworkInstance();
        ScriptFileNodeStepExecutor interpret = new ScriptFileNodeStepExecutor(frameworkInstance);

        //setup nodeexecutor for local node
        multiTestNodeExecutor testexec = new multiTestNodeExecutor();
        NodeExecutorService service = NodeExecutorService.getInstanceForFramework(getFrameworkInstance());
        service.registerInstance("local", testexec);

        testFileCopier testcopier = new testFileCopier();
        FileCopierService copyservice = FileCopierService.getInstanceForFramework(getFrameworkInstance());
        copyservice.registerInstance("local", testcopier);

        //execute command interpreter on local node
        final NodeEntryImpl test1 = new NodeEntryImpl("testhost1", "test1");
        test1.setOsFamily("unix");

        final StepExecutionContext context = ExecutionContextImpl.builder()
            .frameworkProject(PROJ_NAME)
            .framework(frameworkInstance)
            .user("blah")
            .build();
        final InputStream inputStream = new ByteArrayInputStream(new byte[]{0});


        ScriptFileCommand command = new ScriptFileCommandBase() {

            public InputStream getScriptAsStream() {
                return inputStream;
            }

        };
        {
            final ArrayList<NodeExecutorResult> nodeExecutorResults = new ArrayList<NodeExecutorResult>();
            nodeExecutorResults.add(new NodeExecutorResultImpl(true, null, 1));
            nodeExecutorResults.add(new NodeExecutorResultImpl(true, null, 2));
            testexec.testResult = nodeExecutorResults;

            //set filecopier to throw exception
            testcopier.throwException=true;
            testcopier.testResult = "/test/file/path";
            final NodeStepResult interpreterResult;
            try {
                interpreterResult = interpret.executeNodeStep(context, command, test1);
                fail("interpreter should have thrown exception");
            } catch (NodeStepException e) {
                assertTrue(e.getCause() instanceof FileCopierException);
                assertEquals("copyFileStream test", e.getCause().getMessage());
            }
        }
    }

}
