/*
 * Copyright 2019-present Facebook, Inc.
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
package com.facebook.buck.core.starlark.rule.attr;

import com.facebook.buck.core.cell.CellPathResolver;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.rules.providers.Provider;
import com.facebook.buck.core.rules.providers.ProviderInfo;
import com.facebook.buck.core.rules.providers.collect.ProviderInfoCollection;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.rules.coercer.CoerceFailedException;
import com.facebook.buck.rules.coercer.TypeCoercer;
import com.google.common.base.Joiner;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableMap;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.List;
import org.immutables.value.Value;

/** Representation of a parameter of a user defined rule */
public abstract class Attribute<CoercedType> implements AttributeHolder {

  private static final PostCoercionTransform<
          ImmutableMap<BuildTarget, ProviderInfoCollection>, Object>
      IDENTITY =
          (Object object, ImmutableMap<BuildTarget, ProviderInfoCollection> additionalData) ->
              object;

  @Override
  public Attribute<?> getAttribute() {
    return this;
  }

  /**
   * Get the generic type parameters of {@code CoercedType}, if any.
   *
   * @returns A list of the generic types of {@code CoercedType}. For example, {@link
   *     Attribute<com.google.common.collect.ImmutableList<String>>} would return {@link Type}
   *     objects for {@link String}. {@link Attribute<Integer>} would return an empty array.
   */
  @Value.Lazy
  public Type[] getGenericTypes() {
    Class<?> clazz = this.getClass();

    /**
     * We might be pretty far down the class hierarchy. Get to where we're just below {@link
     * Attribute}
     */
    while (!Object.class.equals(clazz)) {
      if (Attribute.class.equals(clazz.getSuperclass())) {
        // Get {@code CoercedType}; if it's something parameterized, return its types,
        // otherwise return an empty list of types
        Type coercedType =
            ((ParameterizedType) clazz.getGenericSuperclass()).getActualTypeArguments()[0];
        if (coercedType instanceof ParameterizedType) {
          return ((ParameterizedType) coercedType).getActualTypeArguments();
        }
        return new Type[0];

      } else {
        clazz = clazz.getSuperclass();
      }
    }
    throw new IllegalStateException(
        String.format(
            "Called Attribute.getGenericTypes on object of type %s that is not an Attribute",
            clazz.getName()));
  }

  /**
   * The default value to use if no value is provided.
   *
   * <p>This will be validated against {@link #getTypeCoercer()}, so may return a value whose type
   * does not match {@link CoercedType} (e.g. a list of strings, rather than a list of {@link
   * com.facebook.buck.core.sourcepath.SourcePath})
   */
  public abstract Object getPreCoercionDefaultValue();

  /** The docstring to use for this attribute */
  public abstract String getDoc();

  /** Whether this attribute is mandatory or not */
  public abstract boolean getMandatory();

  /**
   * The type coercer to use to convert raw values from the parser into something usable internally.
   * This coercer also performs validation
   */
  public abstract TypeCoercer<CoercedType> getTypeCoercer();

  /**
   * Validates values post-coercion to ensure other properties besides 'type' are valid
   *
   * @param value The value to check
   * @throws CoerceFailedException if the value is invalid (e.g. not in a list of pre-approved
   *     values)
   */
  protected void validateCoercedValue(CoercedType value) throws CoerceFailedException {}

  /**
   * @return a method that transforms a coerced value into something more useful to users, taking
   *     into account the rule's dependencies
   */
  public PostCoercionTransform<ImmutableMap<BuildTarget, ProviderInfoCollection>, ?>
      getPostCoercionTransform() {
    return IDENTITY;
  }

  /**
   * Get the coerced value for this attribute.
   *
   * @param cellRoots The cell roots
   * @param projectFilesystem The project file system
   * @param pathRelativeToProjectRoot The path relative to the project root
   * @param targetConfiguration The configuration for this target
   * @param value The object that is to be coerced. This generally comes directly from the parser.
   * @throws CoerceFailedException if the value could not be coerced
   */
  public CoercedType getValue(
      CellPathResolver cellRoots,
      ProjectFilesystem projectFilesystem,
      Path pathRelativeToProjectRoot,
      TargetConfiguration targetConfiguration,
      Object value)
      throws CoerceFailedException {
    CoercedType coercedValue =
        getTypeCoercer()
            .coerce(
                cellRoots,
                projectFilesystem,
                pathRelativeToProjectRoot,
                targetConfiguration,
                value);
    validateCoercedValue(coercedValue);
    return coercedValue;
  }

  /** Helper method to validate that a value is in a list and throw a reasonable error if not */
  protected void validateValueInList(List<CoercedType> values, CoercedType value)
      throws CoerceFailedException {
    if (values.isEmpty()) {
      return;
    }
    if (values.contains(value)) {
      return;
    }
    throw new CoerceFailedException(
        String.format(
            "must be one of '%s' instead of '%s'", Joiner.on("', '").join(values), value));
  }

  /**
   * Ensures that all of the {@link Provider}s in {@code expectedProviders} are present in {@code
   * providerInfos}
   *
   * <p>Note that it is enforced that all build rules return at least {@link
   * com.facebook.buck.core.rules.providers.lib.DefaultInfo}, so that is not required to be
   * specified
   *
   * @param expectedProviders The providers that ALL MUST be present. If empty, no providers are
   *     required, and all {@code providerInfos} will be considered valid.
   * @param target the target that {@code providerInfos} is associated with. Used in errors.
   * @param providerInfos The {@link ProviderInfo}s for a given dependency
   * @throws com.google.common.base.VerifyException if a provider is missing in {@code
   *     providerInfos}
   */
  protected static void validateProvidersPresent(
      List<Provider<?>> expectedProviders,
      BuildTarget target,
      ProviderInfoCollection providerInfos) {
    if (expectedProviders.isEmpty()) {
      return;
    }
    for (Provider<?> provider : expectedProviders) {
      Verify.verify(
          providerInfos.contains(provider),
          "expected provider %s to be present for %s, but it was not",
          provider,
          target);
    }
  }
}
