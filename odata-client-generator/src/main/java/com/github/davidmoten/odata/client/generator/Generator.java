package com.github.davidmoten.odata.client.generator;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.oasisopen.odata.csdl.v4.Schema;
import org.oasisopen.odata.csdl.v4.TComplexType;
import org.oasisopen.odata.csdl.v4.TEntityContainer;
import org.oasisopen.odata.csdl.v4.TEntitySet;
import org.oasisopen.odata.csdl.v4.TEntityType;
import org.oasisopen.odata.csdl.v4.TEnumType;
import org.oasisopen.odata.csdl.v4.TEnumTypeMember;
import org.oasisopen.odata.csdl.v4.TNavigationProperty;
import org.oasisopen.odata.csdl.v4.TProperty;
import org.oasisopen.odata.csdl.v4.TSingleton;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.github.davidmoten.guavamini.Preconditions;
import com.github.davidmoten.odata.client.CollectionPageEntity;
import com.github.davidmoten.odata.client.CollectionPageEntityRequest;
import com.github.davidmoten.odata.client.CollectionPageNonEntity;
import com.github.davidmoten.odata.client.Context;
import com.github.davidmoten.odata.client.ContextPath;
import com.github.davidmoten.odata.client.EntityPreconditions;
import com.github.davidmoten.odata.client.EntityRequest;
import com.github.davidmoten.odata.client.EntityRequestOptions;
import com.github.davidmoten.odata.client.ODataEntity;
import com.github.davidmoten.odata.client.SchemaInfo;
import com.github.davidmoten.odata.client.internal.ChangedFields;
import com.github.davidmoten.odata.client.internal.RequestHelper;

public final class Generator {

    private static final String COLLECTION_PREFIX = "Collection(";
    private final Schema schema;
    private final Names names;

    public Generator(Options options, Schema schema) {
        this.schema = schema;
        this.names = Names.clearOutputDirectoryAndCreate(schema, options);
    }

    public void generate() {

        writeSchemaInfo();

        // write enums
        Util.types(schema, TEnumType.class) //
                .forEach(x -> writeEnum(x));

        // write entityTypes
        Util.types(schema, TEntityType.class) //
                .forEach(x -> writeEntity(x));

        // write complexTypes
        Util.types(schema, TComplexType.class) //
                .forEach(x -> writeComplexType(x));

        // write collection requests
        Util.types(schema, TEntityType.class) //
                .forEach(x -> writeCollectionRequest(x));

        // write containers
        Util.types(schema, TEntityContainer.class) //
                .forEach(x -> writeContainer(x));

        // write single requests
        Util.types(schema, TEntityType.class) //
                .forEach(x -> writeEntityRequest(x));

        // TODO write actions

        // TODO write functions

        // TODO consume annotations for documentation

    }

    private void writeSchemaInfo() {
        String simpleClassName = names.getSimpleClassNameSchema();
        Imports imports = new Imports(simpleClassName);
        Indent indent = new Indent();
        try {
            StringWriter w = new StringWriter();
            try (PrintWriter p = new PrintWriter(w)) {
                p.format("package %s;\n\n", names.getPackageSchema());
                p.format("IMPORTSHERE");
                p.format("public enum %s implements %s {\n\n", simpleClassName, imports.add(SchemaInfo.class));

                // add enum
                p.format("%sINSTANCE;\n\n", indent.right());

                // add fields for entities map
                p.format("%sprivate final %s<%s,%s<? extends %s>> entities = new %s<>();\n\n", //
                        indent, //
                        imports.add(Map.class), //
                        imports.add(String.class), //
                        imports.add(Class.class), //
                        imports.add(ODataEntity.class), //
                        imports.add(HashMap.class));

                // add private constructor
                p.format("%sprivate %s() {\n", indent, simpleClassName);
                indent.right();
                Util.filter(schema.getComplexTypeOrEntityTypeOrTypeDefinition(), TEntityType.class) //
                        .forEach(x -> {
                            p.format("%sentities.put(\"%s\", %s.class);\n", indent,
                                    names.getFullTypeFromSimpleType(x.getName()),
                                    imports.add(names.getFullClassNameEntity(x.getName())));
                        });
                indent.left();

                p.format("%s}\n\n", indent);

                // add method
                p.format("%s@%s\n", indent, imports.add(Override.class));
                p.format("%spublic %s<? extends %s> getEntityClassFromTypeWithNamespace(%s name) {\n", //
                        indent, //
                        imports.add(Class.class), //
                        imports.add(ODataEntity.class), //
                        imports.add(String.class));
                p.format("%sreturn entities.get(name);\n", indent.right());
                p.format("%s}\n\n", indent.left());

                // close class
                p.format("}\n");
            }
            byte[] bytes = w.toString().replace("IMPORTSHERE", imports.toString()).getBytes(StandardCharsets.UTF_8);
            Files.write(names.getClassFileSchema().toPath(), bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void writeEnum(TEnumType t) {
        String simpleClassName = names.getSimpleClassNameEnum(t.getName());
        Imports imports = new Imports(simpleClassName);
        Indent indent = new Indent();
        try {
            StringWriter w = new StringWriter();
            try (PrintWriter p = new PrintWriter(w)) {
                p.format("package %s;\n\n", names.getPackageEnum());
                p.format("IMPORTSHERE");
                p.format("public enum %s implements %s {\n", simpleClassName,
                        imports.add(com.github.davidmoten.odata.client.Enum.class));

                // add members
                indent.right();
                String s = Util.filter(t.getMemberOrAnnotation(), TEnumTypeMember.class) //
                        .map(x -> String.format("%s@%s(\"%s\")\n%s%s(\"%s\", \"%s\")", //
                                indent, //
                                imports.add(JsonProperty.class), //
                                x.getName(), //
                                indent, //
                                Names.toConstant(x.getName()), //
                                x.getName(), //
                                x.getValue()))
                        .collect(Collectors.joining(",\n\n"));
                indent.left();
                p.format("\n%s;\n\n", s);

                // add fields
                p.format("%sprivate final %s name;\n", indent.right(), imports.add(String.class));
                p.format("%sprivate final %s value;\n\n", indent, imports.add(String.class));

                // add constructor
                p.format("%sprivate %s(%s name, %s value) {\n", indent, simpleClassName, imports.add(String.class),
                        imports.add(String.class));
                p.format("%sthis.name = name;\n", indent.right());
                p.format("%sthis.value = value;\n", indent);
                p.format("%s}\n\n", indent.left());

                // add methods
                p.format("%s@%s\n", indent, imports.add(Override.class));
                p.format("%spublic %s enumName() {\n", indent, imports.add(String.class));
                p.format("%sreturn name;\n", indent.right());
                p.format("%s}\n\n", indent.left());

                p.format("%s@%s\n", indent, imports.add(Override.class));
                p.format("%spublic %s enumValue() {\n", indent, imports.add(String.class));
                p.format("%sreturn value;\n", indent.right());
                p.format("%s}\n\n", indent.left());

                // close class
                p.format("}\n");
            }
            byte[] bytes = w.toString().replace("IMPORTSHERE", imports.toString()).getBytes(StandardCharsets.UTF_8);
            Files.write(names.getClassFileEnum(t.getName()).toPath(), bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeEntity(TEntityType entityType) {
        StructureEntityType t = new StructureEntityType(entityType, names);
        String simpleClassName = names.getSimpleClassNameEntity(t.getName());
        Imports imports = new Imports(simpleClassName);
        Indent indent = new Indent();

        StringWriter w = new StringWriter();
        try (PrintWriter p = new PrintWriter(w)) {
            p.format("package %s;\n\n", names.getPackageEntity());
            p.format("IMPORTSHERE");

            p.format("@%s(%s.NON_NULL)\n", imports.add(JsonInclude.class), imports.add(Include.class));
            printPropertyOrder(imports, p, t.getProperties());
            p.format("public class %s%s implements %s {\n\n", simpleClassName, getExtendsClause(t, imports),
                    imports.add(ODataEntity.class));

            addContextPathInjectableField(imports, indent, p);

            // add other fields
            printPropertyFields(imports, indent, p, t.getProperties(), t.hasBaseType());

            addUnmappedFieldsField(imports, indent, p);

            p.format("\n%s@%s\n", indent.right(), imports.add(JacksonInject.class));
            p.format("%sprivate final %s changedFields;\n", indent, imports.add(ChangedFields.class));

            // write constructor
            // build constructor parameters
            String props = t.getFields(imports).stream() //
                    .map(f -> String.format("@%s(\"%s\") %s %s", imports.add(JsonProperty.class), //
                            f.propertyName, //
                            f.importedType, //
                            f.fieldName)) //
                    .map(x -> ",\n" + Indent.INDENT + Indent.INDENT + Indent.INDENT + x) //
                    .collect(Collectors.joining());

            p.format("\n%s@%s", indent, imports.add(JsonCreator.class));
            p.format("\n%spublic %s(@%s %s contextPath, @%s %s changedFields, @%s(\"%s\") %s odataType%s) {\n", //
                    indent, //
                    simpleClassName, //
                    imports.add(JacksonInject.class), //
                    imports.add(ContextPath.class), //
                    imports.add(JacksonInject.class), //
                    imports.add(ChangedFields.class), //
                    imports.add(JsonProperty.class), //
                    "@odata.type", //
                    imports.add(String.class), //
                    props);
            indent.right();
            if (t.getBaseType() != null) {
                String superFields = t.getSuperFields(imports) //
                        .stream() //
                        .map(f -> ", " + f.fieldName) //
                        .collect(Collectors.joining());
                p.format("%ssuper(contextPath, changedFields, odataType%s);\n", indent, superFields);
            }
            p.format("%sthis.contextPath = contextPath;\n", indent);
            p.format("%sthis.changedFields = changedFields;\n", indent);
            if (!t.hasBaseType()) {
                p.format("%sthis.odataType = odataType;\n", indent);
            }

            // print constructor field assignments
            t.getProperties() //
                    .forEach(x -> {
                        String fieldName = Names.getIdentifier(x.getName());
                        p.format("%sthis.%s = %s;\n", indent, fieldName, fieldName);
                        if (isCollection(x) && !names.isEntityWithNamespace(names.getType(x))) {
                            p.format("%sthis.%sNextLink = %sNextLink;\n", indent, fieldName, fieldName);
                        }
                    });

            // close constructor
            p.format("%s}\n", indent.left());

            String builderSuffix = t.getBaseType() == null ? "" : simpleClassName;

            p.format("\n%spublic static %s create%s() {\n", indent, simpleClassName, builderSuffix);
            p.format("%sreturn builder%s().build();\n", indent.right(), builderSuffix);
            p.format("%s}\n", indent.left());

            p.format("\n%spublic static Builder builder%s() {\n", indent, builderSuffix);
            p.format("%sreturn new Builder();\n", indent.right());
            p.format("%s}\n", indent.left());

            // write builder
            p.format("\n%spublic static final class Builder {\n", indent);
            indent.right();
            List<Field> fields = t.getFields(imports);

            // write builder fields
            fields //
                    .forEach(f -> {
                        p.format("%s%s %s;\n", indent, f.importedType, f.fieldName);
                    });

            // write builder setters

            fields.forEach(f -> {
                p.format("\n%spublic Builder %s(%s %s) {\n", indent, f.fieldName, f.importedType, f.fieldName);
                p.format("%sthis.%s = %s;\n", indent.right(), f.fieldName, f.fieldName);
                p.format("%sreturn this;\n", indent);
                p.format("%s}\n", indent.left());
            });

            p.format("\n%spublic %s build() {\n", indent, simpleClassName);
            String builderProps = fields.stream().map(f -> ", " + f.fieldName).collect(Collectors.joining());
            p.format("%sreturn new %s(null, %s.EMPTY,\"%s\"%s);\n", indent.right(), simpleClassName,
                    imports.add(ChangedFields.class), names.getFullTypeFromSimpleType(t.getName()), builderProps);
            p.format("%s}\n", indent.left());

            p.format("%s}\n", indent.left());

            p.format("\n%spublic %s getChangedFields() {\n", indent, imports.add(ChangedFields.class));
            p.format("%sreturn changedFields;\n", indent.right());
            p.format("%s}\n", indent.left());

            // write property getter and setters
            printPropertyGetterAndSetters(imports, indent, p, simpleClassName, t.getProperties(), t.getFields(imports),
                    true);
            printNavigationPropertyGetters(imports, indent, p, t.getNavigationProperties());

            addUnmappedFieldsSetterAndGetter(imports, indent, p);

            p.format("\n}\n");
            byte[] bytes = w.toString().replace("IMPORTSHERE", imports.toString()).getBytes(StandardCharsets.UTF_8);
            Files.write(names.getClassFileEntity(t.getName()).toPath(), bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeComplexType(TComplexType complexType) {
        StructureComplexType t = new StructureComplexType(complexType, names);
        String simpleClassName = names.getSimpleClassNameComplexType(t.getName());
        Imports imports = new Imports(simpleClassName);
        Indent indent = new Indent();

        StringWriter w = new StringWriter();
        try (PrintWriter p = new PrintWriter(w)) {
            p.format("package %s;\n\n", names.getPackageComplexType());
            p.format("IMPORTSHERE");

            p.format("public class %s%s {\n\n", simpleClassName, getExtendsClause(t, imports));

            indent.right();
            addContextPathField(imports, indent, p);

            addUnmappedFieldsField(imports, indent, p);

            // write fields from properties
            printPropertyFields(imports, indent, p, t.getProperties(), t.hasBaseType());

            // add constructor
            // build constructor parameters
            String props = t.getFields(imports) //
                    .stream() //
                    .map(f -> String.format("@%s(\"%s\") %s %s", imports.add(JsonProperty.class), //
                            f.propertyName, //
                            f.importedType, //
                            f.fieldName)) //
                    .map(x -> ",\n" + Indent.INDENT + Indent.INDENT + Indent.INDENT + x) //
                    .collect(Collectors.joining());

            // write constructor
            p.format("\n%s@%s", indent, imports.add(JsonCreator.class));
            p.format("\n%spublic %s(@%s %s contextPath, @%s(\"%s\") %s odataType%s) {\n", //
                    indent, //
                    simpleClassName, //
                    imports.add(JacksonInject.class), //
                    imports.add(ContextPath.class), //
                    imports.add(JsonProperty.class), //
                    "@odata.type", //
                    imports.add(String.class), //
                    props);
            if (t.getBaseType() != null) {
                String superFields = t.getSuperFields(imports) //
                        .stream() //
                        .map(f -> ", " + f.fieldName) //
                        .collect(Collectors.joining());
                p.format("%ssuper(contextPath, odataType%s);\n", indent.right(), superFields);
                indent.left();
            }
            p.format("%sthis.contextPath = contextPath;\n", indent.right());
            if (t.getBaseType() == null) {
                p.format("%sthis.odataType = odataType;\n", indent);
            }
            // print constructor field assignments
            t.getProperties() //
                    .forEach(x -> {
                        String fieldName = Names.getIdentifier(x.getName());
                        p.format("%sthis.%s = %s;\n", indent, fieldName, fieldName);
                        if (isCollection(x) && !names.isEntityWithNamespace(names.getType(x))) {
                            p.format("%sthis.%sNextLink = %sNextLink;\n", indent, fieldName, fieldName);
                        }
                    });
            p.format("%s}\n", indent.left());

            printPropertyGetterAndSetters(imports, indent, p, simpleClassName, t.getProperties(), t.getFields(imports),
                    false);

            addUnmappedFieldsSetterAndGetter(imports, indent, p);

            p.format("\n}\n");
            byte[] bytes = w.toString().replace("IMPORTSHERE", imports.toString()).getBytes(StandardCharsets.UTF_8);
            Files.write(names.getClassFileComplexType(t.getName()).toPath(), bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeEntityRequest(TEntityType t) {
        // TODO only write out those requests needed
        String simpleClassName = names.getSimpleClassNameEntityRequest(t.getName());
        Imports imports = new Imports(simpleClassName);
        Indent indent = new Indent();

        StringWriter w = new StringWriter();
        try (PrintWriter p = new PrintWriter(w)) {
            p.format("package %s;\n\n", names.getPackageEntityRequest());
            p.format("IMPORTSHERE");

            p.format("public final class %s implements %s {\n\n", simpleClassName, imports.add(EntityRequest.class)
                    + "<" + imports.add(names.getFullClassNameEntity(t.getName())) + ">");

            // p.format("%s@%s()\n",indent.right(), imports.add(JsonCreator.class));
            // p.format("%public %s(()\n",indent.right(), imports.add(JsonCreator.class));

            // add field
            indent.right();
            addContextPathField(imports, indent, p);
            p.format("%sprivate final %s id;\n\n", indent, imports.add(String.class));

            // add constructor
            p.format("%spublic %s(%s contextPath, %s id) {\n", indent, simpleClassName, imports.add(ContextPath.class),
                    imports.add(String.class));
            p.format("%sthis.contextPath = contextPath;\n", indent.right());
            p.format("%sthis.id = id;\n", indent);
            p.format("%s}\n\n", indent.left());

            // write get
            p.format("%s@%s\n", indent, imports.add(Override.class));
            p.format("%spublic %s get(%s<%s> options) {\n", indent, //
                    imports.add(names.getFullClassNameFromTypeWithoutNamespace(t.getName())), //
                    imports.add(EntityRequestOptions.class), imports.add(names.getFullClassNameEntity(t.getName())));
            p.format("%sreturn %s.get(contextPath, %s.class, options, %s.INSTANCE);\n", indent.right(),
                    imports.add(RequestHelper.class),
                    imports.add(names.getFullClassNameFromTypeWithoutNamespace(t.getName())), //
                    imports.add(names.getFullClassNameSchema()));
            p.format("%s}\n", indent.left());

            p.format("\n%s@%s\n", indent, imports.add(Override.class));
            p.format("%spublic void delete(%s<%s> options) {\n", indent, //
                    imports.add(EntityRequestOptions.class), imports.add(names.getFullClassNameEntity(t.getName())));
            p.format("%sthrow new %s(); \n", indent.right(), imports.add(UnsupportedOperationException.class));
            p.format("%s}\n", indent.left());

            p.format("\n%s@%s\n", indent, imports.add(Override.class));
            p.format("%spublic %s update(%s<%s> options) {\n", indent, //
                    imports.add(names.getFullClassNameFromTypeWithoutNamespace(t.getName())), //
                    imports.add(EntityRequestOptions.class), imports.add(names.getFullClassNameEntity(t.getName())));
            p.format("%sthrow new %s(); \n", indent.right(), imports.add(UnsupportedOperationException.class));
            p.format("%s}\n", indent.left());

            p.format("\n%s@%s\n", indent, imports.add(Override.class));
            p.format("%spublic %s patch(%s<%s> options, %s entity) {\n", indent, //
                    imports.add(names.getFullClassNameFromTypeWithoutNamespace(t.getName())), //
                    imports.add(EntityRequestOptions.class), //
                    imports.add(names.getFullClassNameEntity(t.getName())), //
                    imports.add(names.getFullClassNameEntity(t.getName())));
            p.format("%sreturn %s.patch(entity, contextPath, %s.class, options, %s.INSTANCE);\n", indent.right(),
                    imports.add(RequestHelper.class),
                    imports.add(names.getFullClassNameFromTypeWithoutNamespace(t.getName())), //
                    imports.add(names.getFullClassNameSchema()));
            p.format("%s}\n", indent.left());
            indent.left();

            Util.filter(t.getKeyOrPropertyOrNavigationProperty(), TNavigationProperty.class) //
                    .filter(x -> names.isEntityWithNamespace(names.getInnerType(names.getType(x)))) //
                    .forEach(x -> {
                        indent.right();
                        final String returnClass;
                        String y = x.getType().get(0);
                        if (y.startsWith(COLLECTION_PREFIX)) {
                            String inner = names.getInnerType(y);
                            returnClass = imports.add(CollectionPageEntityRequest.class) + "<"
                                    + imports.add(names.getFullClassNameFromTypeWithNamespace(inner)) + ", "
                                    + imports.add(names.getFullClassNameEntityRequestFromTypeWithNamespace(inner))
                                    + ">";
                        } else {
                            returnClass = imports.add(names.getFullClassNameEntityRequestFromTypeWithNamespace(y));
                        }
                        p.format("\n%spublic %s %s() {\n", //
                                indent, //
                                returnClass, //
                                Names.getGetterMethodWithoutGet(x.getName()));
                        if (isCollection(x)) {
                            p.format("%sreturn new %s(\n", indent.right(), toType(x, imports));
                            p.format("%scontextPath.addSegment(\"%s\"),\n", indent.right().right().right().right(),
                                    x.getName());
                            p.format("%s%s.class,\n", indent, imports.add(
                                    names.getFullClassNameFromTypeWithNamespace(names.getInnerType(names.getType(x)))));
                            p.format("%s(contextPath, id) -> new %s(contextPath, id), %s.INSTANCE);\n", indent,
                                    imports.add(names.getFullClassNameEntityRequestFromTypeWithNamespace(
                                            names.getInnerType(names.getType(x)))),
                                    imports.add(names.getFullClassNameSchema()));
                            indent.left().left().left().left();
                        } else {
                            p.format("%sreturn new %s(contextPath.addSegment(\"%s\"), \"%s\");\n", indent.right(),
                                    returnClass, x.getName(), x.getName());
                        }
                        p.format("%s}\n", indent.left());

                        // if collection then add with id method
                        if (y.startsWith(COLLECTION_PREFIX)) {
                            // TODO use actual key name from metadata
                            String inner = names.getInnerType(y);
                            if (names.isEntityWithNamespace(inner)) {
                                String entityRequestType = names
                                        .getFullClassNameEntityRequestFromTypeWithNamespace(inner);
                                p.format("\n%spublic %s %s(%s id) {\n", indent, imports.add(entityRequestType),
                                        Names.getIdentifier(x.getName()), imports.add(String.class));
                                p.format("%sreturn new %s(contextPath.addSegment(\"%s\").addKeys(id), id);\n",
                                        indent.right(), imports.add(entityRequestType), x.getName());
                                p.format("%s}\n", indent.left());
                            } else {
                                p.format("\n%spublic %s %s(%s id) {\n", indent, //
                                        imports.add(names.getFullClassNameEntityRequestFromTypeWithNamespace(inner)), //
                                        Names.getGetterMethodWithoutGet(x.getName()), imports.add(String.class));
                                p.format("%sreturn new %s(\n", indent.right(), toType(x, imports));
                                p.format("%scontextPath.addSegment(\"%s\").addKeys(id),\n",
                                        indent.right().right().right().right(), x.getName());
                                p.format("%s%s.class,\n", indent, imports.add(names
                                        .getFullClassNameFromTypeWithNamespace(names.getInnerType(names.getType(x)))));
                                p.format("%s(contextPath, id) -> new %s(contextPath, id), %s.INSTANCE);\n", indent,
                                        imports.add(names.getFullClassNameEntityRequestFromTypeWithNamespace(
                                                names.getInnerType(names.getType(x)))), //
                                        imports.add(names.getFullClassNameSchema()));
                                p.format("%s}\n", indent.left());

                            }
                        }
                        indent.left();
                    });
            p.format("\n}\n");
            byte[] bytes = w.toString().replace("IMPORTSHERE", imports.toString()).getBytes(StandardCharsets.UTF_8);
            Files.write(names.getClassFileEntityRequest(t.getName()).toPath(), bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeContainer(TEntityContainer t) {
        String simpleClassName = names.getSimpleClassNameContainer(t.getName());
        Imports imports = new Imports(simpleClassName);
        Indent indent = new Indent();

        StringWriter w = new StringWriter();
        try (PrintWriter p = new PrintWriter(w)) {
            p.format("package %s;\n\n", names.getPackageContainer());
            p.format("IMPORTSHERE");

            final String extension;
            if (t.getExtends() != null) {
                extension = " extends " + imports.add(names.getFullClassNameFromTypeWithNamespace(t.getExtends()));
            } else {
                extension = "";
            }
            p.format("public final class %s%s {\n\n", simpleClassName, extension);

            // write fields
            p.format("%sprivate final %s contextPath;\n\n", indent.right(), imports.add(ContextPath.class));

            // write constructor
            p.format("%spublic %s(%s context) {\n", indent, simpleClassName, imports.add(Context.class));
            p.format("%sthis.contextPath = new %s(context, context.service().getBasePath());\n", indent.right(),
                    imports.add(ContextPath.class));
            p.format("%s}\n", indent.left());

            // write get methods from properties
            Util.filter(t.getEntitySetOrActionImportOrFunctionImport(), TEntitySet.class) //
                    .forEach(x -> {
                        p.format("\n%spublic %s %s() {\n", indent, toType(x, imports),
                                Names.getIdentifier(x.getName()));
                        p.format("%sreturn new %s(\n", indent.right(), toType(x, imports));
                        p.format("%scontextPath.addSegment(\"%s\"),\n", indent.right().right().right().right(),
                                x.getName());
                        p.format("%s%s.class,\n", indent,
                                imports.add(names.getFullClassNameFromTypeWithNamespace(x.getEntityType())));
                        p.format("%s(contextPath, id) -> new %s(contextPath, id), %s.INSTANCE);\n", indent,
                                imports.add(
                                        names.getFullClassNameEntityRequestFromTypeWithNamespace(x.getEntityType())), //
                                imports.add(names.getFullClassNameSchema()));
                        p.format("%s}\n", indent.left().left().left().left().left());

                        if (names.isEntityWithNamespace(x.getEntityType())) {
                            String entityRequestType = names
                                    .getFullClassNameEntityRequestFromTypeWithNamespace(x.getEntityType());
                            p.format("\n%spublic %s %s(%s id) {\n", indent, imports.add(entityRequestType),
                                    Names.getIdentifier(x.getName()), imports.add(String.class));
                            p.format("%sreturn new %s(contextPath.addSegment(\"%s\").addKeys(id), id);\n",
                                    indent.right(), imports.add(entityRequestType), x.getName());
                            p.format("%s}\n", indent.left());
                        }
                    });

            Util.filter(t.getEntitySetOrActionImportOrFunctionImport(), TSingleton.class) //
                    .forEach(x -> {
                        String importedType = toType(x, imports);
                        p.format("\n%spublic %s %s() {\n", indent, importedType, Names.getIdentifier(x.getName()));
                        p.format("%sreturn new %s(contextPath.addSegment(\"%s\"), \"%s\");\n", indent.right(),
                                importedType, x.getName(), x.getName());
                        p.format("%s}\n", indent.left());
                    });

            p.format("\n}\n");
            byte[] bytes = w.toString().replace("IMPORTSHERE", imports.toString()).getBytes(StandardCharsets.UTF_8);
            Files.write(names.getClassFileContainer(t.getName()).toPath(), bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeCollectionRequest(TEntityType t) {
        String simpleClassName = names.getSimpleClassNameCollectionRequest(t.getName());
        Imports imports = new Imports(simpleClassName);
        Indent indent = new Indent();

        StringWriter w = new StringWriter();
        try (PrintWriter p = new PrintWriter(w)) {
            p.format("package %s;\n\n", names.getPackageCollectionRequest());
            p.format("IMPORTSHERE");
            p.format("public final class %s extends %s<%s, %s>{\n\n", simpleClassName,
                    imports.add(CollectionPageEntityRequest.class),
                    imports.add(names.getFullClassNameFromTypeWithoutNamespace(t.getName())), //
                    imports.add(names.getFullClassNameEntityRequest(t.getName())));

            indent.right();
            addContextPathField(imports, indent, p);

            // add constructor
            p.format("\n%spublic %s(%s contextPath) {\n", indent, simpleClassName, imports.add(ContextPath.class),
                    imports.add(String.class));
            p.format("%ssuper(contextPath, %s.class, (cp, id) -> new %s(cp, id), %s.INSTANCE);\n", indent.right(),
                    imports.add(names.getFullClassNameFromTypeWithoutNamespace(t.getName())), //
                    imports.add(names.getFullClassNameEntityRequestFromTypeWithoutNamespace(t.getName())), //
                    imports.add(names.getFullClassNameSchema()));
            p.format("%sthis.contextPath = contextPath;\n", indent);
            p.format("%s}\n", indent.left());

            // write fields from properties

            Util.filter(t.getKeyOrPropertyOrNavigationProperty(), TNavigationProperty.class) //
                    .forEach(x -> {
                        p.println();
                        if (x.getType().get(0).startsWith(COLLECTION_PREFIX)) {
                            String y = names.getInnerType(names.getType(x));
                            p.format("%spublic %s %s() {\n", //
                                    indent, // tional`
                                    imports.add(names.getFullClassNameCollectionRequestFromTypeWithNamespace(y)), //
                                    x.getName());

                            p.format("%sreturn new %s(contextPath.addSegment(\"%s\"));\n", //
                                    indent.right(), //
                                    imports.add(names.getFullClassNameCollectionRequestFromTypeWithNamespace(y)), //
                                    x.getName());
                            p.format("%s}\n", indent.left());

                            if (names.isEntityWithNamespace(y)) {
                                String entityRequestType = names.getFullClassNameEntityRequestFromTypeWithNamespace(y);
                                p.format("\n%spublic %s %s(%s id) {\n", indent, imports.add(entityRequestType),
                                        Names.getIdentifier(x.getName()), imports.add(String.class));
                                p.format("%sreturn new %s(contextPath.addSegment(\"%s\").addKeys(id), id);\n",
                                        indent.right(), imports.add(entityRequestType), x.getName());
                                p.format("%s}\n", indent.left());
                            }
                        }
                    });
            indent.left();
            p.format("\n}\n");
            byte[] bytes = w.toString().replace("IMPORTSHERE", imports.toString()).getBytes(StandardCharsets.UTF_8);
            Files.write(names.getClassFileCollectionRequest(t.getName()).toPath(), bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private String getExtendsClause(Structure<?> t, Imports imports) {
        if (t.getBaseType() != null) {
            return " extends " + imports.add(names.getFullClassNameFromTypeWithNamespace(t.getBaseType()));
        } else {
            return "";
        }
    }

    private static void addUnmappedFieldsField(Imports imports, Indent indent, PrintWriter p) {
        p.format("\n%sprivate %s<%s,%s> unmappedFields;\n", indent, imports.add(Map.class), imports.add(String.class),
                imports.add(String.class));
    }

    private static void addUnmappedFieldsSetterAndGetter(Imports imports, Indent indent, PrintWriter p) {
        p.format("\n%s@%s\n", indent, imports.add(JsonAnySetter.class));
        // TODO protect "setUnmappedField" name against clashes
        p.format("%sprivate void setUnmappedField(String name, String value) {\n", indent);
        p.format("%sif (unmappedFields == null) {\n", indent.right());
        p.format("%sunmappedFields = new %s<>();\n", indent.right(), imports.add(HashMap.class));
        p.format("%s}\n", indent.left());
        p.format("%sunmappedFields.put(name, value);\n", indent);
        p.format("%s}\n", indent.left());

        p.format("\n%spublic Map<String,String> getUnmappedFields() {\n", indent);
        p.format("%sreturn unmappedFields == null? %s.emptyMap(): unmappedFields;\n", indent.right(),
                imports.add(Collections.class));
        p.format("%s}\n", indent.left());
    }

    private static void addContextPathInjectableField(Imports imports, Indent indent, PrintWriter p) {
        // add context path field
        p.format("%s@%s\n", indent.right(), imports.add(JacksonInject.class));
        addContextPathField(imports, indent, p);
    }

    private static void addContextPathField(Imports imports, Indent indent, PrintWriter p) {
        p.format("%sprivate final %s contextPath;\n", indent, imports.add(ContextPath.class));
    }

    private void printPropertyGetterAndSetters(Imports imports, Indent indent, PrintWriter p, String simpleClassName,
            List<TProperty> properties, List<Field> fields, boolean ofEntity) {

        // write getters and setters
        properties //
                .forEach(x -> {
                    String fieldName = Names.getIdentifier(x.getName());
                    String t = names.getType(x);
                    boolean isCollection = isCollection(x);
                    if (isCollection) {
                        String inner = names.getInnerType(t);
                        String importedInnerType = toTypeNonCollection(inner, imports);
                        boolean isEntity = names.isEntityWithNamespace(inner);
                        Class<?> collectionCls;
                        if (isEntity) {
                            collectionCls = CollectionPageEntity.class;
                        } else {
                            collectionCls = CollectionPageNonEntity.class;
                        }
                        p.format("\n%spublic %s<%s> %s() {\n", indent, imports.add(collectionCls), importedInnerType,
                                Names.getGetterMethod(x.getName()));
                        if (isEntity) {
                            p.format("%sreturn %s.from(contextPath.context(), %s, %s.class, %s.INSTANCE);\n",
                                    indent.right(), imports.add(CollectionPageEntity.class), fieldName,
                                    importedInnerType, imports.add(names.getFullClassNameSchema()));
                        } else {
                            p.format("%sreturn new %s<%s>(contextPath, %s.class, %s, %sNextLink);\n", indent.right(),
                                    imports.add(CollectionPageNonEntity.class), importedInnerType, importedInnerType,
                                    fieldName, fieldName);
                        }
                        p.format("%s}\n", indent.left());
                    } else {
                        String importedType = toTypeNonCollection(t, imports);
                        importedType = imports.add(Optional.class) + "<" + importedType + ">";
                        p.format("\n%spublic %s %s() {\n", indent, importedType, Names.getGetterMethod(x.getName()));
                        p.format("%sreturn %s.ofNullable(%s);\n", indent.right(), imports.add(Optional.class),
                                fieldName);
                        p.format("%s}\n", indent.left());
                        p.format("\n%spublic %s %s(%s %s) {\n", indent, simpleClassName,
                                Names.getSetterMethod(x.getName()), importedType, fieldName);
                        if (x.isUnicode() != null && !x.isUnicode()) {
                            p.format("%s%s.checkIsAscii(%s);\n", indent.right(), imports.add(EntityPreconditions.class),
                                    fieldName, fieldName);
                            indent.left();
                        }

                        // prepare parameters to constructor to return immutable copy
                        String params = fields.stream() //
                                .map(field -> {
                                    if (field.name.equals(x.getName())) {
                                        return field.fieldName + ".orElse(null)";
                                    } else {
                                        return field.fieldName;
                                    }
                                }) //
                                .map(a -> ", " + a) //
                                .collect(Collectors.joining());
                        if (ofEntity) {
                            params = "contextPath, changedFields.add(\"" + x.getName() + "\")" + ", odataType" + params;
                        } else {
                            params = "contextPath" + ", odataType" + params;
                        }

                        indent.right();
                        p.format("%s%s.checkNotNull(%s);\n", indent, imports.add(Preconditions.class), fieldName);
                        p.format("%sreturn new %s(%s);\n", indent, simpleClassName, params);
                        // p.format("%sreturn null;\n", indent);
                        p.format("%s}\n", indent.left());
                    }

                });

    }

    private void printPropertyOrder(Imports imports, PrintWriter p, List<TProperty> properties) {
        String props = properties.stream().map(x -> "\n    \"" + x.getName() + "\"") //
                .collect(Collectors.joining(", "));
        p.format("@%s({%s})\n", imports.add(JsonPropertyOrder.class), props);
    }

    private void printPropertyFields(Imports imports, Indent indent, PrintWriter p, List<TProperty> properties,
            boolean hasBaseType) {

        // TODO make a wrapper for TProperty that passes propertyName, fieldName,
        // importedType
        if (!hasBaseType) {
            p.format("\n%s@%s(\"%s\")\n", indent, imports.add(JsonProperty.class), "@odata.type");
            p.format("%sprotected final %s %s;\n", indent, imports.add(String.class), "odataType");
        }
        properties.stream().forEach(x -> {
            p.format("\n%s@%s(\"%s\")\n", indent, imports.add(JsonProperty.class), x.getName());
            p.format("%sprotected final %s %s;\n", indent, names.toImportedType(x, imports),
                    Names.getIdentifier(x.getName()));
            String t = names.getInnerType(names.getType(x));
            if (isCollection(x) && !names.isEntityWithNamespace(t)) {
                p.format("\n%s@%s(\"%s@nextLink\")\n", indent, imports.add(JsonProperty.class), x.getName());
                p.format("%sprotected %s %sNextLink;\n", indent, imports.add(String.class),
                        Names.getIdentifier(x.getName()));
            }
        });
    }

    private void printNavigationPropertyGetters(Imports imports, Indent indent, PrintWriter p,
            List<TNavigationProperty> properties) {
        // write getters
        properties //
                .stream() //
                .forEach(x -> {
                    String typeName = toType(x, imports);
                    p.format("\n%s@%s\n", indent, imports.add(JsonIgnore.class));
                    p.format("%spublic %s %s() {\n", indent, typeName, Names.getGetterMethod(x.getName()));
                    if (isCollection(x)) {
                        if (names.isEntityWithNamespace(names.getType(x))) {
                            p.format("%sreturn new %s(\n", indent.right(), toType(x, imports));
                            p.format("%scontextPath.addSegment(\"%s\"),\n", indent.right().right().right().right(),
                                    x.getName());
                            p.format("%s%s.class,\n", indent, imports.add(
                                    names.getFullClassNameFromTypeWithNamespace(names.getInnerType(names.getType(x)))));
                            p.format("%s(contextPath, id) -> new %s(contextPath, id), %s.INSTANCE);\n", indent,
                                    imports.add(names.getFullClassNameEntityRequestFromTypeWithNamespace(
                                            names.getInnerType(names.getType(x)))), //
                                    imports.add(names.getFullClassNameSchema()));
                            indent.left().left().left().left();
                        } else {
                            throw new RuntimeException("unexpected");
                        }
                    } else {
                        p.format("%sreturn null; // TODO\n", indent.right());
                    }
                    p.format("%s}\n", indent.left());
                });
    }

    private String toType(TNavigationProperty x, Imports imports) {
        Preconditions.checkArgument(x.getType().size() == 1);
        String t = x.getType().get(0);
        if (!isCollection(x)) {
            if (x.isNullable() != null && x.isNullable()) {
                String r = names.toType(t, imports, List.class);
                return imports.add(Optional.class) + "<" + r + ">";
            } else {
                // is navigation property so must be an entity and is a single request
                return imports.add(names.getFullClassNameEntityRequestFromTypeWithNamespace(t));
            }
        } else {
            return names.toType(t, imports, CollectionPageEntityRequest.class);
        }
    }

    private String toType(TSingleton x, Imports imports) {
        String t = x.getType();
        if (!isCollection(x.getType())) {
            return imports.add(names.getFullClassNameEntityRequestFromTypeWithNamespace(t));
        } else {
            return names.toType(t, imports, CollectionPageEntityRequest.class);
        }
    }

    private String toType(TEntitySet x, Imports imports) {
        String t = x.getEntityType();
        // an entity set is always a collection
        return names.wrapCollection(imports, CollectionPageEntityRequest.class, t);
    }

    private boolean isCollection(TProperty x) {
        return isCollection(names.getType(x));
    }

    private boolean isCollection(TNavigationProperty x) {
        return isCollection(names.getType(x));
    }

    private static boolean isCollection(String t) {
        return t.startsWith(COLLECTION_PREFIX) && t.endsWith(")");
    }

    private String toTypeNonCollection(String t, Imports imports) {
        if (t.startsWith("Edm.")) {
            return names.toTypeFromEdm(t, imports);
        } else if (t.startsWith(schema.getNamespace())) {
            return imports.add(names.getFullClassNameFromTypeWithNamespace(t));
        } else {
            throw new RuntimeException("unhandled type: " + t);
        }
    }

}