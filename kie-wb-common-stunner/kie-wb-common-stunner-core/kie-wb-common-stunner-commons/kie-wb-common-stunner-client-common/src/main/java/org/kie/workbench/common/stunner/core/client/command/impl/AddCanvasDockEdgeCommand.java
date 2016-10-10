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

package org.kie.workbench.common.stunner.core.client.command.impl;

import org.kie.workbench.common.stunner.core.client.canvas.AbstractCanvasHandler;
import org.kie.workbench.common.stunner.core.client.command.AbstractCanvasGraphCommand;
import org.kie.workbench.common.stunner.core.client.command.CanvasViolation;
import org.kie.workbench.common.stunner.core.client.shape.MutationContext;
import org.kie.workbench.common.stunner.core.command.Command;
import org.kie.workbench.common.stunner.core.command.CommandResult;
import org.kie.workbench.common.stunner.core.graph.Node;
import org.kie.workbench.common.stunner.core.graph.command.GraphCommandExecutionContext;
import org.kie.workbench.common.stunner.core.graph.command.impl.AddDockEdgeCommand;
import org.kie.workbench.common.stunner.core.rule.RuleViolation;

public final class AddCanvasDockEdgeCommand extends AbstractCanvasGraphCommand {

    protected Node parent;
    protected Node candidate;

    public AddCanvasDockEdgeCommand( final Node parent,
                                     final Node candidate ) {
        this.parent = parent;
        this.candidate = candidate;
    }

    @Override
    public CommandResult<CanvasViolation> doExecute( final AbstractCanvasHandler context ) {
        context.dock( parent, candidate );
        context.applyElementMutation( candidate, MutationContext.STATIC );
        return buildResult();
    }

    @Override
    public CommandResult<CanvasViolation> doUndo( final AbstractCanvasHandler context ) {
        final DeleteCanvasDockEdgeCommand command = new DeleteCanvasDockEdgeCommand( parent, candidate );
        return command.execute( context );
    }

    @Override
    protected Command<GraphCommandExecutionContext, RuleViolation> buildGraphCommand( final AbstractCanvasHandler context ) {
        return new AddDockEdgeCommand( parent, candidate );
    }

}
