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

package org.kie.workbench.common.stunner.core.client.shape.view.event;

public final class MouseDoubleClickEvent extends AbstractMouseEvent {

    private boolean isButtonLeft;
    private boolean isButtonMiddle;
    private boolean isButtonRight;

    public MouseDoubleClickEvent( final double mouseX,
                                  final double mouseY,
                                  final double clientX,
                                  final double clientY ) {
        super( mouseX, mouseY, clientX, clientY );
    }

    public boolean isButtonLeft() {
        return isButtonLeft;
    }

    public void setButtonLeft( boolean buttonLeft ) {
        isButtonLeft = buttonLeft;
    }

    public boolean isButtonMiddle() {
        return isButtonMiddle;
    }

    public void setButtonMiddle( boolean buttonMiddle ) {
        isButtonMiddle = buttonMiddle;
    }

    public boolean isButtonRight() {
        return isButtonRight;
    }

    public void setButtonRight( boolean buttonRight ) {
        isButtonRight = buttonRight;
    }
}
