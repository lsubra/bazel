// Copyright 2016 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.bazel.rules.android;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.util.ActionsTestUtil;
import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.FileConfiguredTarget;
import com.google.devtools.build.lib.analysis.actions.SpawnAction;
import com.google.devtools.build.lib.analysis.util.BuildViewTestCase;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.rules.android.AndroidResourcesProvider;
import com.google.devtools.build.lib.rules.android.AndroidResourcesProvider.ResourceContainer;
import com.google.devtools.build.lib.rules.java.JavaRuleOutputJarsProvider;
import com.google.devtools.build.lib.rules.java.JavaRuleOutputJarsProvider.OutputJar;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link com.google.devtools.build.lib.rules.android.AarImport}. */
@RunWith(JUnit4.class)
public class AarImportTest extends BuildViewTestCase {
  @Before
  public void setup() throws Exception {
    scratch.file("a/BUILD",
        "aar_import(",
        "    name = 'foo',",
        "    aar = 'foo.aar',",
        ")",
        "aar_import(",
        "    name = 'bar',",
        "    aar = 'bar.aar',",
        "    exports = [':foo', '//java:baz'],",
        ")");
    scratch.file("java/BUILD",
        "android_binary(",
        "    name = 'app',",
        "    manifest = 'AndroidManifest.xml',",
        "    deps = ['//a:bar'],",
        ")",
        "java_import(",
        "    name = 'baz',",
        "    jars = ['baz.jar'],",
        "    constraints = ['android'],",
        ")");
  }

  @Test
  public void testResourcesProvided() throws Exception {
    ConfiguredTarget aarImportTarget = getConfiguredTarget("//a:foo");

    NestedSet<ResourceContainer> directResources = aarImportTarget
        .getProvider(AndroidResourcesProvider.class)
        .getDirectAndroidResources();
    assertThat(directResources).hasSize(1);

    ResourceContainer resourceContainer = directResources.iterator().next();
    assertThat(resourceContainer.getManifest()).isNotNull();

    Iterable<Artifact> resourceArtifacts = resourceContainer.getArtifacts();
    assertThat(resourceArtifacts).hasSize(1);

    Artifact resourceTreeArtifact = resourceArtifacts.iterator().next();
    assertThat(resourceTreeArtifact.isTreeArtifact()).isTrue();
    assertThat(resourceTreeArtifact.getExecPathString()).endsWith("_aar/unzipped/foo");
  }

  @Test
  public void testClassesJarProvided() throws Exception {
    ConfiguredTarget aarImportTarget = getConfiguredTarget("//a:foo");

    Iterable<OutputJar> outputJars =
        aarImportTarget.getProvider(JavaRuleOutputJarsProvider.class).getOutputJars();
    assertThat(outputJars).hasSize(1);

    Artifact classesJar = outputJars.iterator().next().getClassJar();
    assertThat(classesJar.getFilename()).isEqualTo("classes.jar");

    SpawnAction unzipAction = (SpawnAction) getGeneratingAction(classesJar);
    assertThat(unzipAction.getCommandFilename()).endsWith("aar_embedded_jars_extractor");
  }

  @Test
  public void testNoCustomJavaPackage() throws Exception {
    ResourceContainer resourceContainer = getConfiguredTarget("//a:foo")
        .getProvider(AndroidResourcesProvider.class)
        .getDirectAndroidResources()
        .iterator()
        .next();

    // aar_import should not set a custom java package. Instead aapt will read the
    // java package from the manifest.
    assertThat(resourceContainer.getJavaPackage()).isNull();
  }

  @Test
  public void testExportsPropagatesClassesJar() throws Exception {
    FileConfiguredTarget appTarget = getFileConfiguredTarget("//java:app.apk");
    Set<Artifact> artifacts = actionsTestUtil().artifactClosureOf(appTarget.getArtifact());

    ConfiguredTarget bar = getConfiguredTarget("//a:bar");
    Artifact barClassesJar =
        ActionsTestUtil.getFirstArtifactEndingWith(artifacts, "bar/classes.jar");
    // Verify that bar/classes.jar was in the artifact closure.
    assertThat(barClassesJar).isNotNull();
    assertThat(barClassesJar.getArtifactOwner().getLabel()).isEqualTo(bar.getLabel());

    ConfiguredTarget foo = getConfiguredTarget("//a:foo");
    Artifact fooClassesJar =
        ActionsTestUtil.getFirstArtifactEndingWith(artifacts, "foo/classes.jar");
    // Verify that foo/classes.jar was in the artifact closure.
    assertThat(fooClassesJar).isNotNull();
    assertThat(fooClassesJar.getArtifactOwner().getLabel()).isEqualTo(foo.getLabel());

    ConfiguredTarget baz = getConfiguredTarget("//java:baz.jar");
    Artifact bazJar =
        ActionsTestUtil.getFirstArtifactEndingWith(artifacts, "baz.jar");
    // Verify that baz.jar was in the artifact closure
    assertThat(bazJar).isNotNull();
    assertThat(bazJar.getArtifactOwner().getLabel()).isEqualTo(baz.getLabel());
  }

  @Test
  public void testExportsPropagatesResources() throws Exception {
    FileConfiguredTarget appTarget = getFileConfiguredTarget("//java:app.apk");
    Set<Artifact> artifacts = actionsTestUtil().artifactClosureOf(appTarget.getArtifact());

    ConfiguredTarget bar = getConfiguredTarget("//a:bar");
    Artifact barResources =
        ActionsTestUtil.getFirstArtifactEndingWith(artifacts, "_aar/unzipped/bar");
    assertThat(barResources).isNotNull();
    assertThat(barResources.getArtifactOwner().getLabel()).isEqualTo(bar.getLabel());

    ConfiguredTarget foo = getConfiguredTarget("//a:foo");
    Artifact fooResources =
        ActionsTestUtil.getFirstArtifactEndingWith(artifacts, "_aar/unzipped/foo");
    assertThat(fooResources).isNotNull();
    assertThat(fooResources.getArtifactOwner().getLabel()).isEqualTo(foo.getLabel());
  }
}
