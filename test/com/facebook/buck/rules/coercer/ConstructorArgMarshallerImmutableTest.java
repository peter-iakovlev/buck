/*
 * Copyright 2013-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.rules.coercer;

import static com.facebook.buck.core.cell.TestCellBuilder.createCellRoots;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.core.config.FakeBuckConfig;
import com.facebook.buck.core.description.arg.ConstructorArg;
import com.facebook.buck.core.description.arg.Hint;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.model.AbstractRuleType;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.ConfigurationBuildTargetFactoryForTests;
import com.facebook.buck.core.model.EmptyTargetConfiguration;
import com.facebook.buck.core.model.RuleType;
import com.facebook.buck.core.model.TargetConfigurationTransformer;
import com.facebook.buck.core.model.impl.DefaultTargetConfiguration;
import com.facebook.buck.core.model.impl.ImmutableDefaultTargetConfiguration;
import com.facebook.buck.core.model.impl.MultiPlatformTargetConfigurationTransformer;
import com.facebook.buck.core.model.impl.ThrowingTargetConfigurationTransformer;
import com.facebook.buck.core.model.platform.TargetPlatformResolver;
import com.facebook.buck.core.model.platform.impl.ConstraintBasedPlatform;
import com.facebook.buck.core.model.platform.impl.DefaultPlatform;
import com.facebook.buck.core.model.platform.impl.MultiPlatform;
import com.facebook.buck.core.rules.knowntypes.KnownNativeRuleTypes;
import com.facebook.buck.core.rules.knowntypes.KnownRuleTypes;
import com.facebook.buck.core.rules.platform.DummyConfigurationRule;
import com.facebook.buck.core.rules.platform.RuleBasedConstraintResolver;
import com.facebook.buck.core.select.NonCopyingSelectableConfigurationContext;
import com.facebook.buck.core.select.SelectableConfigurationContext;
import com.facebook.buck.core.select.SelectorListResolver;
import com.facebook.buck.core.select.TestSelectable;
import com.facebook.buck.core.select.TestSelectableResolver;
import com.facebook.buck.core.select.impl.DefaultSelectorListResolver;
import com.facebook.buck.core.select.impl.ThrowingSelectableConfigurationContext;
import com.facebook.buck.core.select.impl.ThrowingSelectorListResolver;
import com.facebook.buck.core.sourcepath.DefaultBuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.PathSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.util.immutables.BuckStyleImmutable;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.parser.DefaultSelectableConfigurationContext;
import com.facebook.buck.parser.syntax.ImmutableListWithSelects;
import com.facebook.buck.parser.syntax.ImmutableSelectorValue;
import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import org.immutables.value.Value;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ConstructorArgMarshallerImmutableTest {

  public static final BuildTarget TARGET = BuildTargetFactory.newInstance("//example/path:three");
  private Path basePath;
  private ConstructorArgMarshaller marshaller;
  private ProjectFilesystem filesystem;
  private KnownRuleTypes knownRuleTypes;

  @Rule public ExpectedException expected = ExpectedException.none();

  @Before
  public void setUpInspector() {
    basePath = Paths.get("example", "path");
    marshaller = new DefaultConstructorArgMarshaller(new DefaultTypeCoercerFactory());
    filesystem = new FakeProjectFilesystem();
    knownRuleTypes = KnownNativeRuleTypes.of(ImmutableList.of(), ImmutableList.of());
  }

  private <T extends ConstructorArg> T invokePopulate2(
      Class<T> constructorClass,
      Map<String, ?> attributes,
      ImmutableSet.Builder<BuildTarget> declaredDeps)
      throws CoerceFailedException {
    ImmutableSet.Builder<BuildTarget> configurationDeps = ImmutableSet.builder();
    T result =
        marshaller.populate(
            createCellRoots(filesystem),
            filesystem,
            new ThrowingSelectorListResolver(),
            new ThrowingTargetConfigurationTransformer(),
            new ThrowingSelectableConfigurationContext(),
            TARGET,
            builder(constructorClass),
            declaredDeps,
            configurationDeps,
            attributes);
    assertEquals(ImmutableSet.of(), configurationDeps.build());
    return result;
  }

  private <T extends ConstructorArg> T invokePopulate(
      Class<T> constructorClass, Map<String, ?> attributes) throws CoerceFailedException {
    ImmutableSet.Builder<BuildTarget> declaredDeps = ImmutableSet.builder();
    T result = invokePopulate2(constructorClass, attributes, declaredDeps);
    assertEquals(ImmutableSet.of(), declaredDeps.build());
    return result;
  }

  RuleType ruleType(Class<?> dtoClass) {
    return RuleType.of(
        CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, dtoClass.getName()),
        AbstractRuleType.Kind.BUILD);
  }

  <T extends ConstructorArg> ConstructorArgBuilder<T> builder(Class<T> dtoClass) {
    return knownRuleTypes.getConstructorArgBuilder(
        new DefaultTypeCoercerFactory(), ruleType(dtoClass), dtoClass, TARGET);
  }

  @Test
  public void shouldPopulateAStringValue() throws Exception {

    DtoWithString built =
        invokePopulate(DtoWithString.class, ImmutableMap.<String, Object>of("string", "cheese"));

    assertEquals("cheese", built.getString());
  }

  @Test
  public void shouldPopulateABooleanValue() throws Exception {
    DtoWithBoolean built =
        invokePopulate(
            DtoWithBoolean.class,
            ImmutableMap.<String, Object>of(
                "booleanOne", true,
                "booleanTwo", true));

    assertTrue(built.getBooleanOne());
    assertTrue(built.isBooleanTwo());
  }

  @Test
  public void shouldPopulateBuildTargetValues() throws Exception {
    DtoWithBuildTargets built =
        invokePopulate(
            DtoWithBuildTargets.class,
            ImmutableMap.<String, Object>of(
                "target", "//cake:walk",
                "local", ":fish"));

    assertEquals(
        BuildTargetFactory.newInstance(filesystem.getRootPath(), "//cake:walk"), built.getTarget());
    assertEquals(
        BuildTargetFactory.newInstance(filesystem.getRootPath(), "//example/path:fish"),
        built.getLocal());
  }

  @Test
  public void shouldPopulateANumericValue() throws Exception {
    DtoWithLong built =
        invokePopulate(DtoWithLong.class, ImmutableMap.<String, Object>of("number", 42L));

    assertEquals(42, built.getNumber());
  }

  @Test
  public void shouldPopulateAPathValue() throws Exception {
    DtoWithPath built =
        invokePopulate(DtoWithPath.class, ImmutableMap.<String, Object>of("path", "Fish.java"));

    assertEquals(Paths.get("example/path", "Fish.java"), built.getPath());
  }

  @Test
  public void shouldPopulateSourcePaths() throws Exception {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    BuildTarget target = BuildTargetFactory.newInstance("//example/path:peas");
    DtoWithSourcePaths built =
        invokePopulate(
            DtoWithSourcePaths.class,
            ImmutableMap.<String, Object>of(
                "filePath", "cheese.txt",
                "targetPath", ":peas"));

    assertEquals(
        PathSourcePath.of(projectFilesystem, Paths.get("example/path/cheese.txt")),
        built.getFilePath());
    assertEquals(DefaultBuildTargetSourcePath.of(target), built.getTargetPath());
  }

  @Test
  public void shouldPopulateAnImmutableSortedSet() throws Exception {
    BuildTarget t1 = BuildTargetFactory.newInstance("//please/go:here");
    BuildTarget t2 = BuildTargetFactory.newInstance("//example/path:there");

    // Note: the ordering is reversed from the natural ordering
    DtoWithImmutableSortedSet built =
        invokePopulate(
            DtoWithImmutableSortedSet.class,
            ImmutableMap.<String, Object>of(
                "stuff", ImmutableList.of("//please/go:here", ":there")));

    assertEquals(ImmutableSortedSet.of(t2, t1), built.getStuff());
  }

  @Test
  public void shouldPopulateSets() throws Exception {
    DtoWithSetOfPaths built =
        invokePopulate(
            DtoWithSetOfPaths.class,
            ImmutableMap.<String, Object>of("paths", ImmutableList.of("one", "two")));

    assertEquals(
        ImmutableSet.of(Paths.get("example/path/one"), Paths.get("example/path/two")),
        built.getPaths());
  }

  @Test
  public void shouldPopulateLists() throws Exception {
    DtoWithListOfStrings built =
        invokePopulate(
            DtoWithListOfStrings.class,
            ImmutableMap.<String, Object>of("list", ImmutableList.of("alpha", "beta")));

    assertEquals(ImmutableList.of("alpha", "beta"), built.getList());
  }

  @Test
  public void onlyFieldNamedDepsAreConsideredDeclaredDeps() throws Exception {
    String dep = "//is/a/declared:dep";
    String notDep = "//is/not/a/declared:dep";

    BuildTarget declaredDep = BuildTargetFactory.newInstance(dep);

    Map<String, Object> args = new HashMap<>();
    args.put("deps", ImmutableList.of(dep));
    args.put("notdeps", ImmutableList.of(notDep));

    ImmutableSet.Builder<BuildTarget> declaredDeps = ImmutableSet.builder();

    DtoWithDepsAndNotDeps built = invokePopulate2(DtoWithDepsAndNotDeps.class, args, declaredDeps);

    assertEquals(ImmutableSet.of(declaredDep), declaredDeps.build());
    assertEquals(ImmutableSet.of(declaredDep), built.getDeps());
  }

  @Test
  public void fieldsWithIsDepEqualsFalseHintAreNotTreatedAsDeps() throws Exception {
    String dep = "//should/be:ignored";
    Map<String, Object> args = ImmutableMap.of("deps", ImmutableList.of(dep));

    ImmutableSet.Builder<BuildTarget> declaredDeps = ImmutableSet.builder();

    DtoWithFakeDeps built = invokePopulate2(DtoWithFakeDeps.class, args, declaredDeps);

    assertEquals(ImmutableSet.of(), declaredDeps.build());
    assertEquals(ImmutableSet.of(BuildTargetFactory.newInstance(dep)), built.getDeps());
  }

  @Test
  public void collectionsAreOptional() throws Exception {
    DtoWithCollections built = invokePopulate(DtoWithCollections.class, ImmutableMap.of());

    assertEquals(ImmutableSet.of(), built.getSet());
    assertEquals(ImmutableSet.of(), built.getImmutableSet());
    assertEquals(ImmutableSortedSet.of(), built.getSortedSet());
    assertEquals(ImmutableSortedSet.of(), built.getImmutableSortedSet());
    assertEquals(ImmutableList.of(), built.getList());
    assertEquals(ImmutableList.of(), built.getImmutableList());
    assertEquals(ImmutableMap.of(), built.getMap());
    assertEquals(ImmutableMap.of(), built.getImmutableMap());
  }

  @Test
  public void optionalCollectionsWithoutAValueWillBeSetToAnEmptyOptionalCollection()
      throws Exception {
    // Deliberately not populating args
    Map<String, Object> args = ImmutableMap.of();

    DtoWithOptionalSetOfStrings built = invokePopulate(DtoWithOptionalSetOfStrings.class, args);

    assertEquals(Optional.empty(), built.getStrings());
  }

  @Test
  public void errorsOnMissingValues() throws Exception {
    expected.expect(HumanReadableException.class);
    expected.expectMessage(containsString(TARGET.getFullyQualifiedName()));
    expected.expectMessage(containsString("missing required"));
    expected.expectMessage(containsString("booleanOne"));
    expected.expectMessage(containsString("booleanTwo"));

    invokePopulate(DtoWithBoolean.class, ImmutableMap.of());
  }

  @Test
  public void errorsOnBadChecks() throws Exception {
    expected.expect(RuntimeException.class);
    expected.expectMessage(containsString(TARGET.getFullyQualifiedName()));
    expected.expectMessage(containsString("NOT THE SECRETS"));

    invokePopulate(DtoWithCheck.class, ImmutableMap.of("string", "secrets"));
  }

  @Test
  public void noErrorsOnGoodChecks() throws Exception {
    DtoWithCheck built =
        invokePopulate(DtoWithCheck.class, ImmutableMap.of("string", "not secrets"));
    assertEquals("not secrets", built.getString());
  }

  @Test(expected = CoerceFailedException.class)
  public void shouldBeAnErrorToAttemptToSetASingleValueToACollection() throws Exception {
    invokePopulate(
        DtoWithString.class, ImmutableMap.<String, Object>of("string", ImmutableList.of("a", "b")));
  }

  @Test(expected = CoerceFailedException.class)
  public void shouldBeAnErrorToAttemptToSetACollectionToASingleValue() throws Exception {
    invokePopulate(
        DtoWithSetOfStrings.class,
        ImmutableMap.<String, Object>of("strings", "isn't going to happen"));
  }

  @Test(expected = CoerceFailedException.class)
  public void shouldBeAnErrorToSetTheWrongTypeOfValueInACollection() throws Exception {
    invokePopulate(
        DtoWithSetOfStrings.class,
        ImmutableMap.<String, Object>of("strings", ImmutableSet.of(true, false)));
  }

  @Test
  public void shouldNormalizePaths() throws Exception {
    DtoWithPath built =
        invokePopulate(
            DtoWithPath.class, ImmutableMap.<String, Object>of("path", "./bar/././fish.txt"));

    assertEquals(basePath.resolve("bar/fish.txt").normalize(), built.getPath());
  }

  @Test
  public void shouldSetBuildTargetParameters() throws Exception {
    DtoWithBuildTargetList built =
        invokePopulate(
            DtoWithBuildTargetList.class,
            ImmutableMap.<String, Object>of(
                "single", "//com/example:cheese",
                "sameBuildFileTarget", ":cake",
                "targets", ImmutableList.of(":cake", "//com/example:cheese")));

    BuildTarget cheese = BuildTargetFactory.newInstance("//com/example:cheese");
    BuildTarget cake = BuildTargetFactory.newInstance("//example/path:cake");

    assertEquals(cheese, built.getSingle());
    assertEquals(cake, built.getSameBuildFileTarget());
    assertEquals(ImmutableList.of(cake, cheese), built.getTargets());
  }

  @Test
  public void canPopulateSimpleConstructorArgFromBuildFactoryParams() throws Exception {
    BuildTarget target = BuildTargetFactory.newInstance("//example/path:path");

    ImmutableMap<String, Object> args =
        ImmutableMap.<String, Object>builder()
            .put("required", "cheese")
            .put("notRequired", "cake")
            // Long because that's what comes from python.
            .put("num", 42L)
            .put("optionalLong", 88L)
            .put("needed", true)
            // Skipping optional boolean.
            .put("aSrcPath", ":path")
            .put("aPath", "./File.java")
            .put("notAPath", "./NotFile.java")
            .build();
    DtoWithVariousTypes built = invokePopulate(DtoWithVariousTypes.class, args);

    assertEquals("cheese", built.getRequired());
    assertEquals("cake", built.getNotRequired().get());
    assertEquals(42, built.getNum());
    assertEquals(Optional.of(88L), built.getOptionalLong());
    assertTrue(built.isNeeded());
    assertEquals(Optional.empty(), built.isNotNeeded());
    DefaultBuildTargetSourcePath expected = DefaultBuildTargetSourcePath.of(target);
    assertEquals(expected, built.getASrcPath());
    assertEquals(Paths.get("example/path/NotFile.java"), built.getNotAPath().get());
  }

  @Test
  public void shouldNotPopulateDefaultValues() throws Exception {
    // This is not an ImmutableMap so we can test null values.
    Map<String, Object> args = new HashMap<>();
    args.put("defaultString", null);
    args.put("defaultSourcePath", null);
    DtoWithOptionalValues built = invokePopulate(DtoWithOptionalValues.class, args);

    assertEquals(Optional.empty(), built.getNoString());
    assertEquals(Optional.empty(), built.getDefaultString());
    assertEquals(Optional.empty(), built.getNoSourcePath());
    assertEquals(Optional.empty(), built.getDefaultSourcePath());
  }

  @Test
  public void shouldRespectSpecifiedDefaultValues() throws Exception {
    // This is not an ImmutableMap so we can test null values.
    Map<String, Object> args = new HashMap<>();
    args.put("something", null);
    args.put("things", null);
    DtoWithDefaultValues built = invokePopulate(DtoWithDefaultValues.class, args);

    assertEquals("foo", built.getSomething());
    assertEquals(ImmutableList.of("bar"), built.getThings());
    assertEquals(365, built.getMore());
    assertTrue(built.getBeGood());
  }

  @Test
  public void shouldAllowOverridingDefaultValues() throws Exception {
    // This is not an ImmutableMap so we can test null values.
    Map<String, Object> args = new HashMap<>();
    args.put("something", "bar");
    args.put("things", ImmutableList.of("qux", "quz"));
    args.put("more", 1234L);
    args.put("beGood", false);
    DtoWithDefaultValues built = invokePopulate(DtoWithDefaultValues.class, args);

    assertEquals("bar", built.getSomething());
    assertEquals(ImmutableList.of("qux", "quz"), built.getThings());
    assertEquals(1234, built.getMore());
    assertFalse(built.getBeGood());
  }

  @Test
  public void shouldResolveCollectionOfSourcePathsRelativeToTarget() throws Exception {
    DtoWithSetOfSourcePaths built =
        invokePopulate(
            DtoWithSetOfSourcePaths.class,
            ImmutableMap.<String, Object>of(
                "srcs", ImmutableList.of("main.py", "lib/__init__.py", "lib/manifest.py")));

    ImmutableSet<String> observedValues =
        built.getSrcs().stream()
            .map(input -> ((PathSourcePath) input).getRelativePath().toString())
            .collect(ImmutableSet.toImmutableSet());
    assertEquals(
        ImmutableSet.of(
            Paths.get("example/path/main.py").toString(),
            Paths.get("example/path/lib/__init__.py").toString(),
            Paths.get("example/path/lib/manifest.py").toString()),
        observedValues);
  }

  @Test
  public void derivedMethodsAreIgnored() throws Exception {
    DtoWithDerivedAndOrdinaryMethods built =
        invokePopulate(
            DtoWithDerivedAndOrdinaryMethods.class,
            ImmutableMap.<String, Object>of("string", "tamarins"));
    assertEquals("tamarins", built.getString());
    assertEquals("TAMARINS", built.getUpper());
    assertEquals("constant", built.getConstant());
  }

  @Test
  public void specifyingDerivedValuesIsIgnored() throws Exception {
    DtoWithDerivedAndOrdinaryMethods built =
        invokePopulate(
            DtoWithDerivedAndOrdinaryMethods.class,
            ImmutableMap.<String, Object>of(
                "string", "tamarins",
                "upper", "WRONG"));
    assertEquals("tamarins", built.getString());
    assertEquals("TAMARINS", built.getUpper());
  }

  @Test
  public void defaultMethodFallsBackToDefault() throws Exception {
    InheritsFromHasDefaultMethod built =
        invokePopulate(InheritsFromHasDefaultMethod.class, ImmutableMap.of());
    assertEquals("foo", built.getString());
  }

  @Test
  public void defaultMethodCanBeSpecified() throws Exception {
    InheritsFromHasDefaultMethod built =
        invokePopulate(
            InheritsFromHasDefaultMethod.class, ImmutableMap.<String, Object>of("string", "bar"));
    assertEquals("bar", built.getString());
  }

  @Test
  public void populateWithConfiguringAttributesResolvesConfigurableAttributes() throws Exception {
    BuildTarget selectableTarget = ConfigurationBuildTargetFactoryForTests.newInstance("//x:y");
    SelectorListResolver selectorListResolver =
        new DefaultSelectorListResolver(
            new TestSelectableResolver(
                ImmutableList.of(new TestSelectable(selectableTarget, true))));
    ImmutableListWithSelects selectorList =
        ImmutableListWithSelects.of(
            ImmutableList.of(
                ImmutableSelectorValue.of(
                    ImmutableMap.of("DEFAULT", "string1", "//x:y", "string2"), ""),
                ImmutableSelectorValue.of(
                    ImmutableMap.of("DEFAULT", "string3", "//x:y", "string4"), "")),
            ImmutableMap.class);
    TargetPlatformResolver targetPlatformResolver = configuration -> DefaultPlatform.INSTANCE;
    SelectableConfigurationContext selectableConfigurationContext =
        DefaultSelectableConfigurationContext.of(
            FakeBuckConfig.builder().build(),
            new RuleBasedConstraintResolver(DummyConfigurationRule::of),
            EmptyTargetConfiguration.INSTANCE,
            targetPlatformResolver);
    TargetConfigurationTransformer targetConfigurationTransformer =
        new MultiPlatformTargetConfigurationTransformer(targetPlatformResolver);
    ImmutableSet.Builder<BuildTarget> declaredDeps = ImmutableSet.builder();
    ImmutableSet.Builder<BuildTarget> configurationDeps = ImmutableSet.builder();

    DtoWithString dto =
        marshaller.populate(
            createCellRoots(filesystem),
            filesystem,
            selectorListResolver,
            targetConfigurationTransformer,
            selectableConfigurationContext,
            TARGET,
            builder(DtoWithString.class),
            declaredDeps,
            configurationDeps,
            ImmutableMap.<String, Object>of("string", selectorList));

    assertEquals("string2string4", dto.getString());
    assertTrue(declaredDeps.build().isEmpty());
    assertEquals(ImmutableSet.of(selectableTarget), configurationDeps.build());
  }

  @Test
  public void populateWithConfiguringAttributesCopiesValuesToImmutable() throws Exception {
    SelectorListResolver selectorListResolver =
        new DefaultSelectorListResolver(new TestSelectableResolver());
    TargetConfigurationTransformer targetConfigurationTransformer =
        new MultiPlatformTargetConfigurationTransformer(configuration -> DefaultPlatform.INSTANCE);
    ImmutableSet.Builder<BuildTarget> declaredDeps = ImmutableSet.builder();
    DtoWithString dto =
        marshaller.populate(
            createCellRoots(filesystem),
            filesystem,
            selectorListResolver,
            targetConfigurationTransformer,
            NonCopyingSelectableConfigurationContext.INSTANCE,
            TARGET,
            builder(DtoWithString.class),
            declaredDeps,
            ImmutableSet.builder(),
            ImmutableMap.<String, Object>of("string", "value"));
    assertEquals("value", dto.getString());
    assertTrue(declaredDeps.build().isEmpty());
  }

  @Test
  public void populateWithConfiguringAttributesCollectsDeclaredDeps() throws Exception {
    SelectorListResolver selectorListResolver =
        new DefaultSelectorListResolver(new TestSelectableResolver());
    TargetConfigurationTransformer targetConfigurationTransformer =
        new MultiPlatformTargetConfigurationTransformer(configuration -> DefaultPlatform.INSTANCE);
    ImmutableSet.Builder<BuildTarget> declaredDeps = ImmutableSet.builder();
    BuildTarget dep = BuildTargetFactory.newInstance("//a/b:c");
    marshaller.populate(
        createCellRoots(filesystem),
        filesystem,
        selectorListResolver,
        targetConfigurationTransformer,
        NonCopyingSelectableConfigurationContext.INSTANCE,
        TARGET,
        builder(DtoWithDepsAndNotDeps.class),
        declaredDeps,
        ImmutableSet.builder(),
        ImmutableMap.<String, Object>of("deps", ImmutableList.of("//a/b:c")));
    assertEquals(ImmutableSet.of(dep), declaredDeps.build());
  }

  @Test
  public void populateWithConfiguringAttributesSkipsMissingValues() throws Exception {
    SelectorListResolver selectorListResolver =
        new DefaultSelectorListResolver(new TestSelectableResolver());
    TargetConfigurationTransformer targetConfigurationTransformer =
        new MultiPlatformTargetConfigurationTransformer(configuration -> DefaultPlatform.INSTANCE);
    DtoWithOptionalSetOfStrings dto =
        marshaller.populate(
            createCellRoots(filesystem),
            filesystem,
            selectorListResolver,
            targetConfigurationTransformer,
            NonCopyingSelectableConfigurationContext.INSTANCE,
            TARGET,
            builder(DtoWithOptionalSetOfStrings.class),
            ImmutableSet.builder(),
            ImmutableSet.builder(),
            ImmutableMap.of());
    assertFalse(dto.getStrings().isPresent());
  }

  @Test
  public void populateWithConfiguringAttributesSplitsConfiguration() throws Exception {
    BuildTarget multiPlatformTarget =
        ConfigurationBuildTargetFactoryForTests.newInstance("//platform:multi_platform");
    BuildTarget basePlatformTarget =
        ConfigurationBuildTargetFactoryForTests.newInstance("//platform:base_platform");
    BuildTarget nestedPlatform1Target =
        ConfigurationBuildTargetFactoryForTests.newInstance("//platform:nested_platform_1");
    BuildTarget nestedPlatform2Target =
        ConfigurationBuildTargetFactoryForTests.newInstance("//platform:nested_platform_2");

    MultiPlatform multiPlatform =
        new MultiPlatform(
            multiPlatformTarget,
            new ConstraintBasedPlatform(basePlatformTarget, ImmutableSet.of()),
            ImmutableList.of(
                new ConstraintBasedPlatform(nestedPlatform1Target, ImmutableSet.of()),
                new ConstraintBasedPlatform(nestedPlatform2Target, ImmutableSet.of())));
    SelectorListResolver selectorListResolver =
        new DefaultSelectorListResolver(new TestSelectableResolver());
    TargetPlatformResolver targetPlatformResolver = configuration -> multiPlatform;
    TargetConfigurationTransformer targetConfigurationTransformer =
        new MultiPlatformTargetConfigurationTransformer(targetPlatformResolver);
    SelectableConfigurationContext selectableConfigurationContext =
        DefaultSelectableConfigurationContext.of(
            FakeBuckConfig.builder().build(),
            new RuleBasedConstraintResolver(DummyConfigurationRule::of),
            ImmutableDefaultTargetConfiguration.of(multiPlatformTarget),
            targetPlatformResolver);

    DtoWithSplit dto =
        marshaller.populate(
            createCellRoots(filesystem),
            filesystem,
            selectorListResolver,
            targetConfigurationTransformer,
            selectableConfigurationContext,
            TARGET,
            builder(DtoWithSplit.class),
            ImmutableSet.builder(),
            ImmutableSet.builder(),
            ImmutableMap.of("deps", ImmutableList.of("//a/b:c")));

    assertEquals(3, dto.getDeps().size());
    assertEquals(
        ImmutableSet.of(multiPlatformTarget, nestedPlatform1Target, nestedPlatform2Target),
        dto.getDeps().stream()
            .map(BuildTarget::getTargetConfiguration)
            .map(DefaultTargetConfiguration.class::cast)
            .map(DefaultTargetConfiguration::getTargetPlatform)
            .collect(ImmutableSet.toImmutableSet()));
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithString implements ConstructorArg {
    abstract String getString();
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithBoolean implements ConstructorArg {
    abstract boolean getBooleanOne();

    abstract boolean isBooleanTwo();
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithListOfStrings implements ConstructorArg {
    abstract List<String> getList();
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithFakeDeps implements ConstructorArg {
    @Hint(isDep = false)
    abstract Set<BuildTarget> getDeps();

    @Hint(isDep = false)
    abstract Set<BuildTarget> getProvidedDeps();
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithCollections implements ConstructorArg {
    abstract Set<String> getSet();

    abstract ImmutableSet<String> getImmutableSet();

    @Value.NaturalOrder
    abstract SortedSet<String> getSortedSet();

    @Value.NaturalOrder
    abstract ImmutableSortedSet<String> getImmutableSortedSet();

    abstract List<String> getList();

    abstract ImmutableList<String> getImmutableList();

    abstract Map<String, String> getMap();

    abstract ImmutableMap<String, String> getImmutableMap();
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithOptionalSetOfStrings implements ConstructorArg {
    abstract Optional<Set<String>> getStrings();
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithSetOfStrings implements ConstructorArg {
    abstract Set<String> getStrings();
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithPath implements ConstructorArg {
    abstract Path getPath();
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractEmptyImmutableDto implements ConstructorArg {}

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithBuildTargets implements ConstructorArg {
    abstract BuildTarget getTarget();

    abstract BuildTarget getLocal();
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithLong implements ConstructorArg {
    abstract long getNumber();
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithSourcePaths implements ConstructorArg {
    abstract SourcePath getFilePath();

    abstract SourcePath getTargetPath();
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithImmutableSortedSet implements ConstructorArg {
    abstract ImmutableSortedSet<BuildTarget> getStuff();
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithDeclaredDeps implements ConstructorArg {
    abstract ImmutableSet<BuildTarget> getDeps();

    abstract ImmutableSet<SourcePath> getPaths();
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithSetOfPaths implements ConstructorArg {
    abstract Set<Path> getPaths();
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithDepsAndNotDeps implements ConstructorArg {
    abstract Set<BuildTarget> getDeps();

    abstract Set<BuildTarget> getNotDeps();
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithBuildTargetList implements ConstructorArg {
    abstract BuildTarget getSingle();

    abstract BuildTarget getSameBuildFileTarget();

    abstract List<BuildTarget> getTargets();
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithVariousTypes implements ConstructorArg {
    abstract String getRequired();

    abstract Optional<String> getNotRequired();

    abstract int getNum();

    abstract Optional<Long> getOptionalLong();

    abstract boolean isNeeded();

    abstract Optional<Boolean> isNotNeeded();

    abstract SourcePath getASrcPath();

    abstract Optional<SourcePath> getNotASrcPath();

    abstract Path getAPath();

    abstract Optional<Path> getNotAPath();
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithOptionalValues implements ConstructorArg {
    abstract Optional<String> getNoString();

    abstract Optional<String> getDefaultString();

    abstract Optional<SourcePath> getNoSourcePath();

    abstract Optional<SourcePath> getDefaultSourcePath();
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithDefaultValues implements ConstructorArg {
    @Value.Default
    public String getSomething() {
      return "foo";
    }

    @Value.Default
    public List<String> getThings() {
      return ImmutableList.of("bar");
    }

    @Value.Default
    public int getMore() {
      return 365;
    }

    @Value.Default
    public Boolean getBeGood() {
      return true;
    }
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithSetOfSourcePaths implements ConstructorArg {
    abstract ImmutableSortedSet<SourcePath> getSrcs();
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithCheck implements ConstructorArg {
    abstract String getString();

    @Value.Check
    public void check() {
      Preconditions.checkState(!getString().equals("secrets"), "NOT THE SECRETS!");
    }
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithDerivedAndOrdinaryMethods implements ConstructorArg {
    abstract String getString();

    public String getConstant() {
      return "constant";
    }

    @Value.Derived
    public String getUpper() {
      return getString().toUpperCase();
    }
  }

  interface HasDefaultMethod extends ConstructorArg {
    @Value.Default
    default String getString() {
      return "foo";
    }
  }

  @BuckStyleImmutable
  @Value.Immutable
  interface AbstractInheritsFromHasDefaultMethod extends HasDefaultMethod {}

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractDtoWithSplit implements ConstructorArg {
    @Hint(splitConfiguration = true)
    abstract ImmutableSortedSet<BuildTarget> getDeps();
  }
}
