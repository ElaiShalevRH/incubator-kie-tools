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

package org.kie.workbench.common.stunner.core.client.session.impl;

import org.kie.workbench.common.stunner.core.client.canvas.AbstractCanvas;
import org.kie.workbench.common.stunner.core.client.canvas.AbstractCanvasHandler;
import org.kie.workbench.common.stunner.core.client.canvas.controls.pan.PanControl;
import org.kie.workbench.common.stunner.core.client.canvas.controls.select.SelectionControl;
import org.kie.workbench.common.stunner.core.client.canvas.controls.zoom.ZoomControl;
import org.kie.workbench.common.stunner.core.graph.Element;

public abstract class AbstractReadOnlySession extends AbstractCanvasSession
        implements DefaultCanvasReadOnlySession {

    SelectionControl<AbstractCanvasHandler, Element> selectionControl;
    ZoomControl<AbstractCanvas> zoomControl;
    PanControl<AbstractCanvas> panControl;

    public AbstractReadOnlySession( final AbstractCanvas canvas,
                                    final AbstractCanvasHandler canvasHandler,
                                    final SelectionControl<AbstractCanvasHandler, Element> selectionControl,
                                    final ZoomControl<AbstractCanvas> zoomControl,
                                    final PanControl<AbstractCanvas> panControl ) {
        super( canvas, canvasHandler );
        this.selectionControl = selectionControl;
        this.zoomControl = zoomControl;
        this.panControl = panControl;
    }

    @Override
    public SelectionControl<AbstractCanvasHandler, Element> getShapeSelectionControl() {
        return selectionControl;
    }

    @Override
    public ZoomControl<AbstractCanvas> getZoomControl() {
        return zoomControl;
    }

    @Override
    public PanControl<AbstractCanvas> getPanControl() {
        return panControl;
    }

    @Override
    public void doDispose() {
        if ( null != selectionControl ) {
            selectionControl.disable();
            selectionControl = null;
        }
        if ( null != zoomControl ) {
            zoomControl.disable();
            zoomControl = null;
        }
        if ( null != panControl ) {
            panControl.disable();
            panControl = null;
        }

    }

}
