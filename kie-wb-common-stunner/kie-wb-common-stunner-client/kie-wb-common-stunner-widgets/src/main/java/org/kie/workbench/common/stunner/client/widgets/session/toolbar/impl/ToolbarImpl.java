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

package org.kie.workbench.common.stunner.client.widgets.session.toolbar.impl;

import com.google.gwt.logging.client.LogConfiguration;
import org.kie.workbench.common.stunner.client.widgets.session.toolbar.ToolbarView;
import org.kie.workbench.common.stunner.client.widgets.session.toolbar.item.AbstractToolbarItem;
import org.kie.workbench.common.stunner.core.client.session.impl.DefaultCanvasFullSession;

import javax.annotation.PostConstruct;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

// TODO: Drop-down & complex tree buttons support.
@Dependent
public class ToolbarImpl extends AbstractToolbar<DefaultCanvasFullSession> {

    private static Logger LOGGER = Logger.getLogger( ToolbarImpl.class.getName() );

    @Inject
    public ToolbarImpl( final Instance<AbstractToolbarItem<DefaultCanvasFullSession>> toolbarItems,
                        final ToolbarView<DefaultCanvasFullSession> view ) {
        super( toolbarItems, view );
    }

    @PostConstruct
    public void init() {
        super.doInit();
    }

    private void log( final Level level, final String message ) {
        if ( LogConfiguration.loggingIsEnabled() ) {
            LOGGER.log( level, message );
        }
    }

}
