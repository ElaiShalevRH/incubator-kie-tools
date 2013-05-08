/*
 * Copyright 2013 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.errai.otec.client;

import static org.jboss.errai.otec.client.operation.OTOperationImpl.createLocalOnlyOperation;
import static org.jboss.errai.otec.client.operation.OTOperationImpl.createOperation;

import org.jboss.errai.otec.client.mutation.CharacterMutation;
import org.jboss.errai.otec.client.mutation.Mutation;
import org.jboss.errai.otec.client.mutation.MutationType;
import org.jboss.errai.otec.client.operation.OTOperation;
import org.jboss.errai.otec.client.operation.OpPair;
import org.jboss.errai.otec.client.util.OTLogUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author Mike Brock
 * @author Christian Sadilek <csadilek@redhat.com>
 */
public class Transformer {
  private final OTEngine engine;
  private final boolean remoteWins;
  private final OTEntity entity;
  private final OTOperation remoteOp;

  private Transformer(final OTEngine engine,
                      final boolean remoteWins,
                      final OTEntity entity,
                      final OTOperation remoteOp) {
    this.engine = engine;
    this.remoteWins = remoteWins;
    this.entity = entity;
    this.remoteOp = remoteOp;
  }

  public static Transformer createTransformerLocalPrecedence(final OTEngine engine, final OTEntity entity,
                                                             final OTOperation operation) {
    return new Transformer(engine, false, entity, operation);
  }

  public static Transformer createTransformerRemotePrecedence(final OTEngine engine, final OTEntity entity,
                                                              final OTOperation operation) {
    return new Transformer(engine, true, entity, operation);
  }

  @SuppressWarnings("unchecked")
  public OTOperation transform() {
    final TransactionLog transactionLog = entity.getTransactionLog();
    List<OTOperation> localOps;
    try {
      if (remoteOp.getRevisionHash().equals(entity.getState().getHash())) {
        localOps = Collections.emptyList();
      }
      else {
        localOps = transactionLog.getLocalOpsSinceRemoteOperation(remoteOp, true);
      }
    }
    catch (OTException e) {
      e.printStackTrace();
      throw new BadSync("", entity.getId(), remoteOp.getAgentId());

      //LogUtil.log("failed while trying to transform: " + remoteOp + " rev:" + remoteOp.getRevision());
    }

    boolean first = true;
    boolean appliedRemoteOp = false;
    OTOperation applyOver = remoteOp;
    if (localOps.isEmpty() && remoteOp.getRevisionHash().equals(entity.getState().getHash())) {
      createOperation(remoteOp).apply(entity);
      return remoteOp;
    }
    else {
      final LogQuery query = transactionLog.getEffectiveStateForRevision(remoteOp.getRevision() + 1);
      entity.getState().syncStateFrom(query.getEffectiveState());

      assert OTLogUtil.log("REWIND",
          "<<FOR TRANSFORM OVER: " + remoteOp + ">>",
          "-",
          engine.getName(),
          remoteOp.getRevision() + 1,
          "\"" + entity.getState().get() + "\"");


      final OTOperation firstOp = localOps.get(0);

      if (!firstOp.getRevisionHash().equals(remoteOp.getRevisionHash())) {
        final List<OTOperation> previousRemoteOpsTo = transactionLog.getPreviousRemoteOpsTo(applyOver, firstOp);

        if (!previousRemoteOpsTo.isEmpty()) {

          // we have a history divergence that we now must deal with.
          localOps.removeAll(previousRemoteOpsTo);

          OTOperation firstPrevRemotePp = previousRemoteOpsTo.get(0);
          while (firstPrevRemotePp.getTransformedFrom() != null) {
            firstPrevRemotePp = firstPrevRemotePp.getTransformedFrom().getRemoteOp();
          }

          final OTOperation lastPrevRemoteOp = previousRemoteOpsTo.get(previousRemoteOpsTo.size() - 1);

          final LogQuery query2 = transactionLog.getEffectiveStateForRevision(firstPrevRemotePp.getRevision() + 1);
          entity.getState().syncStateFrom(query2.getEffectiveState());

          localOps = query2.getLocalOpsNeedsMerge();

          for (final OTOperation operation : localOps) {
            operation.apply(entity, true);
          }

          applyOver = translateFrom(remoteOp, lastPrevRemoteOp);
          createOperation(applyOver).apply(entity);

          assert OTLogUtil.log("CTRNSFRM", "COMPOUND TRANSFORM FOR: " + remoteOp, "-", engine.getName(), remoteOp.getRevision() + 1,
              "\"" + entity.getState().get() + "\"");

          return applyOver;
        }
        else {
          System.out.println("DANGER!");
        }
      }

      for (final OTOperation localOp : localOps) {
        if (first) {
          first = false;
          if (applyOver.getRevisionHash().equals(localOp.getRevisionHash()) || localOp.isResolvedConflict()) {
            applyOver = transform(applyOver, localOp);
          }
          else {
            applyOver = transform(applyOver,
                transform(localOp.getTransformedFrom().getLocalOp(),
                    localOp.getTransformedFrom().getRemoteOp()));
          }
        }
        else {
          OTOperation ot = transform(localOp, applyOver, true);

          final boolean changedLocally = !localOp.equals(ot);

          if (changedLocally) {
            localOp.removeFromCanonHistory();
            entity.decrementRevisionCounter();
          }

          if (!appliedRemoteOp && changedLocally) {
            applyOver.apply(entity);
            appliedRemoteOp = true;
          }

          applyOver = transform(applyOver, ot);

          ot.apply(entity, !changedLocally);

          if (changedLocally) {
            //   localOp.getOuterPath().removeFromCanonHistory();
            localOp.removeFromCanonHistory();
            localOp.setOuterPath(ot);
          }
        }
      }

      if (!appliedRemoteOp) {
        applyOver = createOperation(applyOver);
        applyOver.apply(entity);
      }

      if (applyOver.isResolvedConflict()) {
        return applyOver;
      }
      else {
        return remoteOp;
      }
    }
  }

  private OTOperation translateFrom(final OTOperation remoteOp, final OTOperation basedOn) {
    OpPair transformedFrom = basedOn.getTransformedFrom();
    if (transformedFrom == null) {
      return remoteOp;
    }
    else {
      final List<OpPair> translationVector = new ArrayList<OpPair>();
      OTOperation last = basedOn;
      OTOperation op = basedOn;
      while ((transformedFrom = op.getTransformedFrom()) != null) {
        translationVector.add(transformedFrom);
        op = transformedFrom.getRemoteOp();
        if (last.equals(op)) {
          continue;
        }
        op.unmarkAsResolvedConflict();

        last = op;
      }

      Collections.reverse(translationVector);

      OTOperation applyOver = remoteOp;
      for (OpPair o : translationVector) {
        applyOver = transform(applyOver, transform(o.getLocalOp(), o.getRemoteOp()));
      }

      return applyOver;
    }
  }

  private OTOperation transform(final OTOperation remoteOp, final OTOperation localOp) {
    return transform(remoteOp, localOp, false);
  }

  private OTOperation transform(final OTOperation remoteOp, final OTOperation localOp, final boolean invertWinRule) {
    final boolean remoteWins = invertWinRule ? !this.remoteWins : this.remoteWins;
    final OTOperation transformedOp;
    final List<Mutation> remoteMutations = remoteOp.getMutations();
    final List<Mutation> localMutations = localOp.getMutations();
    final List<Mutation> transformedMutations = new ArrayList<Mutation>(remoteMutations.size());

    final Iterator<Mutation> remoteOpMutations;
    final Iterator<Mutation> localOpMutations;

    if (remoteMutations.size() > localMutations.size()) {
      remoteOpMutations = noopPaddedIterator(remoteMutations, remoteMutations.size());
      localOpMutations = noopPaddedIterator(localMutations, remoteMutations.size());
    }
    else if (remoteMutations.size() < localMutations.size()) {
      remoteOpMutations = noopPaddedIterator(remoteMutations, localMutations.size());
      localOpMutations = noopPaddedIterator(localMutations, localMutations.size());
    }
    else {
      remoteOpMutations = remoteMutations.iterator();
      localOpMutations = localMutations.iterator();
    }

    int offset = 0;
    boolean resolvesConflict = false;

    while (remoteOpMutations.hasNext()) {
      final Mutation rm = remoteOpMutations.next();
      final Mutation lm = localOpMutations.next();

      final int rmIdx = rm.getPosition();
      final int diff = rmIdx - lm.getPosition();

      final TransactionLog transactionLog = entity.getTransactionLog();
      if (diff < 0) {
        if (rm.getType() == MutationType.Delete) {
          if (rm.getPosition() + rm.length() > lm.getPosition()) {
            if (lm.getType() == MutationType.Insert) {
              // uh-oh.. our local insert is inside the range of this remote delete ... move the insert
              // to the beginning of the delete range to resolve this.

              final LogQuery effectiveStateForRevision = transactionLog.getEffectiveStateForRevision(localOp.getRevision());
              final State rewind = effectiveStateForRevision.getEffectiveState();
              localOp.removeFromCanonHistory();
              transactionLog.markDirty();
              final Mutation mutation = lm.newBasedOn(rm.getPosition());
              mutation.apply(rewind);
              final OTOperation localOnlyOperation = createLocalOnlyOperation(engine, remoteOp.getAgentId(), Collections.singletonList(mutation), entity, localOp.getRevision(), OpPair.of(remoteOp, localOp));
              transactionLog.insertLog(localOp.getRevision(), localOnlyOperation);

              entity.getState().syncStateFrom(rewind);

              transformedMutations.add(rm.newBasedOn(mutation.getPosition() + lm.length()));

              continue;

              //transformedMutations.add(StringMutation.of(MutationType.Insert, rm.g));
            }
            else if (lm.getType() == MutationType.Delete) {
              final int truncate = lm.getPosition() - rm.getPosition();
              final Mutation mutation = rm.newBasedOn(rm.getPosition(), truncate);

              transformedMutations.add(mutation);
              resolvesConflict = true;

              continue;
            }
          }

        }

        if (rm.getType() != MutationType.Noop) {
          transformedMutations.add(rm);
        }
      }
      else if (diff == 0) {
        if (remoteOp.getRevision() != localOp.getRevision() && !remoteOp.isResolvedConflict()) {
          transformedMutations.add(rm);
        }
        else {
          switch (rm.getType()) {
            case Insert:
              if (!remoteWins && lm.getType() == MutationType.Insert) {
                offset += lm.length();
              }
              resolvesConflict = true;
              break;
            case Delete:
              if (lm.getType() == MutationType.Insert) {
                offset += lm.length();
                resolvesConflict = true;
              }
              break;
          }
          if (resolvesConflict) {
            transformedMutations.add(adjustMutationToIndex(rmIdx + offset, rm));
          }
        }
      }
      else if (diff > 0) {
        if (lm.getType() != MutationType.Noop && !localOp.isResolvedConflict()) {
          switch (rm.getType()) {
            case Insert:
              if (lm.getType() == MutationType.Insert) {
                offset += lm.length();
              }
              if (lm.getType() == MutationType.Delete) {
                if (remoteWins && (lm.getPosition() + lm.length()) > rm.getPosition()) {
                  final LogQuery effectiveStateForRevision = transactionLog.getEffectiveStateForRevision(localOp.getRevision());
                  final State rewind
                      = effectiveStateForRevision.getEffectiveState();
                  final Mutation mutation = rm.newBasedOn(lm.getPosition());
                  //transactionLog.pruneFromOperation(localOp);

                  localOp.removeFromCanonHistory();
                  transactionLog.markDirty();

                  mutation.apply(rewind);
                  entity.getState().syncStateFrom(rewind);

                  transactionLog.insertLog(remoteOp.getRevision(),
                      createLocalOnlyOperation(engine, remoteOp.getAgentId(), Collections.singletonList(mutation), entity, remoteOp.getRevision(), OpPair.of(remoteOp, localOp)));
                  transformedMutations.add(lm.newBasedOn(mutation.getPosition() + rm.length()));

                  continue;
                }

                offset -= lm.length();
              }
              break;
            case Delete:
              if (lm.getType() == MutationType.Insert) {
                offset += lm.length();
              }
              if (lm.getType() == MutationType.Delete) {
                if (remoteWins && (lm.getPosition() + lm.length()) > rm.getPosition()) {
                  final LogQuery effectiveStateForRevision = transactionLog.getEffectiveStateForRevision(localOp.getRevision());
                  final State rewind
                      = effectiveStateForRevision.getEffectiveState();

                  rm.apply(rewind);

                  transactionLog.insertLog(localOp.getRevision(),
                      remoteOp);

                  localOp.removeFromCanonHistory();

                  entity.getState().syncStateFrom(rewind);

                  final int truncate = rm.getPosition() - lm.getPosition();
                  final Mutation mutation = lm.newBasedOn(lm.getPosition(), truncate);

                  transformedMutations.add(mutation);
                  resolvesConflict = true;

                  continue;
                }

                //             offset -= lm.length();
              }
              break;
          }
        }

        transformedMutations.add(adjustMutationToIndex(rmIdx + offset, rm));
      }
    }

    final OpPair of = OpPair.of(remoteOp, localOp);

    transformedOp =
        createLocalOnlyOperation(engine, remoteOp.getAgentId(), transformedMutations, entity,
            of);

    if (resolvesConflict || remoteOp.isResolvedConflict()) {
      transformedOp.markAsResolvedConflict();
    }

    assert OTLogUtil.log("TRANSFORM",
        remoteOp + " , " + localOp + " -> " + transformedOp,
        "-",
        engine.getName(),
        remoteOp.getRevision(),
        "\"" + entity.getState().get() + "\"");

    return transformedOp;
  }

  private static Iterator<Mutation> noopPaddedIterator(final List<Mutation> mutationList, final int largerSize) {
    return new Iterator<Mutation>() {
      int pos = 0;
      final CharacterMutation paddedMutation = CharacterMutation.noop(mutationList.get(mutationList.size() - 1)
          .getPosition());
      final Iterator<Mutation> iteratorDelegate = mutationList.iterator();

      @Override
      public boolean hasNext() {
        return pos < largerSize;
      }

      @Override
      public Mutation next() {
        if (pos++ < mutationList.size()) {
          return iteratorDelegate.next();
        }
        else {
          return paddedMutation;
        }
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  public static Collection<OTOperation> opCombinitator(OTEngine engine, List<OTOperation> toCombine) {
    if (toCombine.size() == 1) {
      return toCombine;
    }

    final List<Mutation> mutationList = new ArrayList<Mutation>();
    for (final OTOperation op : toCombine) {
      mutationList.addAll(op.getMutations());
    }

    final Mutation combined = mutationCombinitator(mutationList);

    if (combined == null) {
      return toCombine;
    }
    else {
      final List<OTOperation> combinedOps = new ArrayList<OTOperation>();
      final OTOperation operation
          = createOperation(engine,
          toCombine.get(0).getAgentId(),
          Collections.singletonList(combined),
          toCombine.get(0).getEntityId(),
          -1,
          toCombine.get(0).getRevisionHash());

      return Collections.singletonList(operation);
    }
  }

  @SuppressWarnings("unchecked")
  public static Mutation mutationCombinitator(final Collection<Mutation> toCombine) {
    Mutation last = null;
    for (final Mutation m : toCombine) {
      if (last != null) {
        last = m.combineWith(last);
        if (last == null) {
          return null;
        }
      }
      else {
        last = m;
      }
    }

    return last;
  }

  private Mutation adjustMutationToIndex(int idx, Mutation mutation) {
    return (idx == mutation.getPosition()) ? mutation : mutation.newBasedOn(idx);
  }

  public String toString() {
    return String.valueOf(entity.getState().get());
  }
}
