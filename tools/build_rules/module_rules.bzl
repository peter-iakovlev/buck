# Copyright 2018-present Facebook, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may
# not use this file except in compliance with the License. You may obtain
# a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations
# under the License.

"""Contains build rules for Buck modules"""

load("@bazel_skylib//lib:collections.bzl", "collections")
load("//tools/build_rules:java_rules.bzl", "java_library_with_plugins")
load("//tools/build_rules:module_rules_for_tests.bzl", "convert_module_deps_to_test")

def buck_module(
        name,
        module_deps = [],
        module_resources = [],
        **kwargs):
    """Declares a buck module

    Args:
      name: name
      module_deps: A list of modules this module depends on
      module_resources: A list of files that needs to be placed along a module
      **kwargs: kwargs
    """

    kwargs["provided_deps"] = collections.uniq(kwargs.get("provided_deps", []) + [
        "//src/com/facebook/buck/core/module:module",
    ] + module_deps)

    java_library_with_plugins(
        name = name,
        **kwargs
    )

    jar_without_hash_name = name + "_jar_without_hash"

    native.java_binary(
        name = jar_without_hash_name,
        deps = [
            ":" + name,
        ],
    )

    calculate_module_hash_name = name + "_calculate_module_hash"

    native.genrule(
        name = calculate_module_hash_name,
        out = "module-binary-hash.txt",
        cmd = " ".join([
            "$(exe //py/hash:hash_files)",
            "$(location :{})".format(jar_without_hash_name),
            "$(location //py/hash:hash_files.py) > $OUT",
        ]),
    )

    meta_inf_name = name + "-meta-inf"
    native.genrule(
        name = meta_inf_name,
        out = "META-INF",
        cmd = " ".join([
            "mkdir $OUT && ",
            "cp $(location :{}) $OUT/module-binary-hash.txt".format(calculate_module_hash_name),
        ]),
    )

    module_name = name + "-module"
    native.zip_file(
        name = module_name,
        out = "{}.jar".format(name),
        srcs = [
            ":" + meta_inf_name,
        ],
        zip_srcs = [
            ":" + jar_without_hash_name,
        ],
        visibility = [
            "//programs:bucklib",
            "//programs:calculate-buck-binary-hash",
            "//test/...",
        ],
    )

    final_module_jar_name = name + "-module-jar"
    native.prebuilt_jar(
        name = final_module_jar_name,
        binary_jar = ":" + module_name,
    )

    # This target is not used directly by module rules, but by `java_test` to get access
    # to all provided dependencies of the current module.
    native.java_library(
        name = name + "_module_for_test",
        exported_deps = depset([":" + final_module_jar_name]) +
                        kwargs.get("provided_deps", []) +
                        kwargs.get("exported_provided_deps", []) +
                        convert_module_deps_to_test(module_deps),
        visibility = ["PUBLIC"],
    )

    native.filegroup(
        name = name + "_resources",
        srcs = module_resources,
        visibility = ["PUBLIC"],
    )

def get_module_binary(module):
    """ Returns target for module's binary """
    return "{}-module".format(module)

def convert_modules_to_resources(buck_modules):
    """ Converts modules to a map with resources for packaging in a Python binary """
    result = {}

    for k, v in buck_modules.items():
        result["buck-modules/{}.jar".format(k)] = get_module_binary(v)

    return result

def convert_modules_to_external_resources(buck_modules, modules_with_resources):
    """ Converts modules to a map with resources to keep them outside of module jars """
    result = {}

    for module in modules_with_resources:
        result["buck-modules-resources/{}".format(module)] = "{}_resources".format(buck_modules[module])

    return result
