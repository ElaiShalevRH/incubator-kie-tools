/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.workbench.common.stunner.bpmn.backend.marshall.json.oryx.property;

import org.kie.workbench.common.stunner.core.definition.property.PropertyType;
import org.kie.workbench.common.stunner.core.definition.property.type.IntegerType;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class IntegerTypeSerializer implements Bpmn2OryxPropertySerializer<Integer> {

    @Override
    public boolean accepts( PropertyType type ) {
        return IntegerType.name.equals( type.getName() );
    }

    @Override
    public Integer parse( Object property, String value ) {
        return Integer.parseInt( value );
    }

    @Override
    public String serialize( Object property, Integer value ) {
        return Integer.toString( value );
    }
}
