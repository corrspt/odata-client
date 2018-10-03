package com.github.davidmoten.odata.client.generator.model;

import org.oasisopen.odata.csdl.v4.TPropertyRef;

import com.github.davidmoten.odata.client.generator.Imports;
import com.github.davidmoten.odata.client.generator.Names;

public class PropertyRef {

    private final TPropertyRef value;
    private final EntityType entityType;
    private final Names names;

    public PropertyRef(TPropertyRef value, EntityType entityType, Names names) {
        this.value = value;
        this.entityType = entityType;
        this.names = names;
    }

    public String getName() {
        return value.getName();
    }

    public String getAlias() {
        return value.getAlias();
    }

    public String getFieldName() {
        return Names.getIdentifier(value.getName());
    }

    public String getImportedClassNameForReferredPropertyType(Imports imports) {
        return entityType //
                .getHeirarchy() //
                .stream() //
                .flatMap(x -> x.getProperties().stream()) //
                .filter(x -> x.getName().equals(getName())) //
                // note that type should not be collection because is a key property
                .map(x -> names.toImportedTypeNonCollection(x, imports)) //
                .findFirst() //
                .get();
    }

}
