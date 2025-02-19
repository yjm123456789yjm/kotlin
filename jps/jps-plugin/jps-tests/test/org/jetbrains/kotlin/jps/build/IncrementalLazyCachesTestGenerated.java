/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.jps.build;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.util.KtTestUtil;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.TestsPackage}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@RunWith(JUnit3RunnerWithInners.class)
public class IncrementalLazyCachesTestGenerated extends AbstractIncrementalLazyCachesTest {
    @TestMetadata("jps/jps-plugin/testData/incremental/lazyKotlinCaches")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class LazyKotlinCaches extends AbstractIncrementalLazyCachesTest {
        private void runTest(String testDataFilePath) throws Exception {
            KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
        }

        public void testAllFilesPresentInLazyKotlinCaches() throws Exception {
            KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("jps/jps-plugin/testData/incremental/lazyKotlinCaches"), Pattern.compile("^([^\\.]+)$"), null, true);
        }

        @TestMetadata("class")
        public void testClass() throws Exception {
            runTest("jps/jps-plugin/testData/incremental/lazyKotlinCaches/class/");
        }

        @TestMetadata("classInheritance")
        public void testClassInheritance() throws Exception {
            runTest("jps/jps-plugin/testData/incremental/lazyKotlinCaches/classInheritance/");
        }

        @TestMetadata("constant")
        public void testConstant() throws Exception {
            runTest("jps/jps-plugin/testData/incremental/lazyKotlinCaches/constant/");
        }

        @TestMetadata("function")
        public void testFunction() throws Exception {
            runTest("jps/jps-plugin/testData/incremental/lazyKotlinCaches/function/");
        }

        @TestMetadata("inlineFunctionWithUsage")
        public void testInlineFunctionWithUsage() throws Exception {
            runTest("jps/jps-plugin/testData/incremental/lazyKotlinCaches/inlineFunctionWithUsage/");
        }

        @TestMetadata("inlineFunctionWithoutUsage")
        public void testInlineFunctionWithoutUsage() throws Exception {
            runTest("jps/jps-plugin/testData/incremental/lazyKotlinCaches/inlineFunctionWithoutUsage/");
        }

        @TestMetadata("noKotlin")
        public void testNoKotlin() throws Exception {
            runTest("jps/jps-plugin/testData/incremental/lazyKotlinCaches/noKotlin/");
        }

        @TestMetadata("topLevelPropertyAccess")
        public void testTopLevelPropertyAccess() throws Exception {
            runTest("jps/jps-plugin/testData/incremental/lazyKotlinCaches/topLevelPropertyAccess/");
        }

        @TestMetadata("jps/jps-plugin/testData/incremental/lazyKotlinCaches/class")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class Class extends AbstractIncrementalLazyCachesTest {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
            }

            public void testAllFilesPresentInClass() throws Exception {
                KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("jps/jps-plugin/testData/incremental/lazyKotlinCaches/class"), Pattern.compile("^([^\\.]+)$"), null, true);
            }
        }

        @TestMetadata("jps/jps-plugin/testData/incremental/lazyKotlinCaches/classInheritance")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class ClassInheritance extends AbstractIncrementalLazyCachesTest {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
            }

            public void testAllFilesPresentInClassInheritance() throws Exception {
                KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("jps/jps-plugin/testData/incremental/lazyKotlinCaches/classInheritance"), Pattern.compile("^([^\\.]+)$"), null, true);
            }
        }

        @TestMetadata("jps/jps-plugin/testData/incremental/lazyKotlinCaches/constant")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class Constant extends AbstractIncrementalLazyCachesTest {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
            }

            public void testAllFilesPresentInConstant() throws Exception {
                KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("jps/jps-plugin/testData/incremental/lazyKotlinCaches/constant"), Pattern.compile("^([^\\.]+)$"), null, true);
            }
        }

        @TestMetadata("jps/jps-plugin/testData/incremental/lazyKotlinCaches/function")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class Function extends AbstractIncrementalLazyCachesTest {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
            }

            public void testAllFilesPresentInFunction() throws Exception {
                KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("jps/jps-plugin/testData/incremental/lazyKotlinCaches/function"), Pattern.compile("^([^\\.]+)$"), null, true);
            }
        }

        @TestMetadata("jps/jps-plugin/testData/incremental/lazyKotlinCaches/inlineFunctionWithUsage")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class InlineFunctionWithUsage extends AbstractIncrementalLazyCachesTest {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
            }

            public void testAllFilesPresentInInlineFunctionWithUsage() throws Exception {
                KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("jps/jps-plugin/testData/incremental/lazyKotlinCaches/inlineFunctionWithUsage"), Pattern.compile("^([^\\.]+)$"), null, true);
            }
        }

        @TestMetadata("jps/jps-plugin/testData/incremental/lazyKotlinCaches/inlineFunctionWithoutUsage")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class InlineFunctionWithoutUsage extends AbstractIncrementalLazyCachesTest {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
            }

            public void testAllFilesPresentInInlineFunctionWithoutUsage() throws Exception {
                KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("jps/jps-plugin/testData/incremental/lazyKotlinCaches/inlineFunctionWithoutUsage"), Pattern.compile("^([^\\.]+)$"), null, true);
            }
        }

        @TestMetadata("jps/jps-plugin/testData/incremental/lazyKotlinCaches/noKotlin")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class NoKotlin extends AbstractIncrementalLazyCachesTest {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
            }

            public void testAllFilesPresentInNoKotlin() throws Exception {
                KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("jps/jps-plugin/testData/incremental/lazyKotlinCaches/noKotlin"), Pattern.compile("^([^\\.]+)$"), null, true);
            }
        }

        @TestMetadata("jps/jps-plugin/testData/incremental/lazyKotlinCaches/topLevelPropertyAccess")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class TopLevelPropertyAccess extends AbstractIncrementalLazyCachesTest {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
            }

            public void testAllFilesPresentInTopLevelPropertyAccess() throws Exception {
                KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("jps/jps-plugin/testData/incremental/lazyKotlinCaches/topLevelPropertyAccess"), Pattern.compile("^([^\\.]+)$"), null, true);
            }
        }
    }

    @TestMetadata("jps/jps-plugin/testData/incremental/changeIncrementalOption")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class ChangeIncrementalOption extends AbstractIncrementalLazyCachesTest {
        private void runTest(String testDataFilePath) throws Exception {
            KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
        }

        public void testAllFilesPresentInChangeIncrementalOption() throws Exception {
            KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("jps/jps-plugin/testData/incremental/changeIncrementalOption"), Pattern.compile("^([^\\.]+)$"), null, true);
        }

        @TestMetadata("incrementalOff")
        public void testIncrementalOff() throws Exception {
            runTest("jps/jps-plugin/testData/incremental/changeIncrementalOption/incrementalOff/");
        }

        @TestMetadata("incrementalOffOn")
        public void testIncrementalOffOn() throws Exception {
            runTest("jps/jps-plugin/testData/incremental/changeIncrementalOption/incrementalOffOn/");
        }

        @TestMetadata("incrementalOffOnJavaChanged")
        public void testIncrementalOffOnJavaChanged() throws Exception {
            runTest("jps/jps-plugin/testData/incremental/changeIncrementalOption/incrementalOffOnJavaChanged/");
        }

        @TestMetadata("incrementalOffOnJavaOnly")
        public void testIncrementalOffOnJavaOnly() throws Exception {
            runTest("jps/jps-plugin/testData/incremental/changeIncrementalOption/incrementalOffOnJavaOnly/");
        }

        @TestMetadata("jps/jps-plugin/testData/incremental/changeIncrementalOption/incrementalOff")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class IncrementalOff extends AbstractIncrementalLazyCachesTest {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
            }

            public void testAllFilesPresentInIncrementalOff() throws Exception {
                KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("jps/jps-plugin/testData/incremental/changeIncrementalOption/incrementalOff"), Pattern.compile("^([^\\.]+)$"), null, true);
            }
        }

        @TestMetadata("jps/jps-plugin/testData/incremental/changeIncrementalOption/incrementalOffOn")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class IncrementalOffOn extends AbstractIncrementalLazyCachesTest {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
            }

            public void testAllFilesPresentInIncrementalOffOn() throws Exception {
                KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("jps/jps-plugin/testData/incremental/changeIncrementalOption/incrementalOffOn"), Pattern.compile("^([^\\.]+)$"), null, true);
            }
        }

        @TestMetadata("jps/jps-plugin/testData/incremental/changeIncrementalOption/incrementalOffOnJavaChanged")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class IncrementalOffOnJavaChanged extends AbstractIncrementalLazyCachesTest {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
            }

            public void testAllFilesPresentInIncrementalOffOnJavaChanged() throws Exception {
                KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("jps/jps-plugin/testData/incremental/changeIncrementalOption/incrementalOffOnJavaChanged"), Pattern.compile("^([^\\.]+)$"), null, true);
            }
        }

        @TestMetadata("jps/jps-plugin/testData/incremental/changeIncrementalOption/incrementalOffOnJavaOnly")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class IncrementalOffOnJavaOnly extends AbstractIncrementalLazyCachesTest {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
            }

            public void testAllFilesPresentInIncrementalOffOnJavaOnly() throws Exception {
                KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("jps/jps-plugin/testData/incremental/changeIncrementalOption/incrementalOffOnJavaOnly"), Pattern.compile("^([^\\.]+)$"), null, true);
            }
        }
    }
}
