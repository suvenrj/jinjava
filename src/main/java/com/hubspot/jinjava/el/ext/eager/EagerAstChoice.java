package com.hubspot.jinjava.el.ext.eager;

import com.hubspot.jinjava.el.ext.DeferredParsingException;
import de.odysseus.el.tree.Bindings;
import de.odysseus.el.tree.impl.ast.AstChoice;
import de.odysseus.el.tree.impl.ast.AstNode;
import javax.el.ELContext;
import javax.el.ELException;

public class EagerAstChoice extends AstChoice implements EvalResultHolder {
  protected Object evalResult;
  protected final EvalResultHolder question;
  protected final EvalResultHolder yes;
  protected final EvalResultHolder no;

  public EagerAstChoice(AstNode question, AstNode yes, AstNode no) {
    this(
      EagerAstNodeDecorator.getAsEvalResultHolder(question),
      EagerAstNodeDecorator.getAsEvalResultHolder(yes),
      EagerAstNodeDecorator.getAsEvalResultHolder(no)
    );
  }

  private EagerAstChoice(
    EvalResultHolder question,
    EvalResultHolder yes,
    EvalResultHolder no
  ) {
    super((AstNode) question, (AstNode) yes, (AstNode) no);
    this.question = question;
    this.yes = yes;
    this.no = no;
  }

  @Override
  public Object eval(Bindings bindings, ELContext context) throws ELException {
    try {
      evalResult = super.eval(bindings, context);
      return evalResult;
    } catch (DeferredParsingException e) {
      StringBuilder sb = new StringBuilder();
      sb.append(e.getDeferredEvalResult());
      if (question.getAndClearEvalResult() != null) {
        // the question was evaluated so jump to either yes or no
        throw new DeferredParsingException(this, sb.toString());
      }
      sb.append(" ? ");

      sb.append(EvalResultHolder.reconstructNode(bindings, context, yes, e, false));
      sb.append(" : ");
      sb.append(EvalResultHolder.reconstructNode(bindings, context, no, e, false));
      //      if (yes.hasEvalResult()) {
      //        sb.append(ChunkResolver.getValueAsJinjavaStringSafe(yes.getAndClearEvalResult()));
      //      } else {
      //        try {
      //          sb.append(
      //            ChunkResolver.getValueAsJinjavaStringSafe(
      //              ((AstNode) yes).eval(bindings, context)
      //            )
      //          );
      //        } catch (DeferredParsingException e1) {
      //          sb.append(e1.getDeferredEvalResult());
      //        }
      //      }
      //      sb.append(" : ");
      //      if (no.hasEvalResult()) {
      //        sb.append(ChunkResolver.getValueAsJinjavaStringSafe(no.getAndClearEvalResult()));
      //      } else {
      //        try {
      //          sb.append(
      //            ChunkResolver.getValueAsJinjavaStringSafe(
      //              ((AstNode) no).eval(bindings, context)
      //            )
      //          );
      //        } catch (DeferredParsingException e1) {
      //          sb.append(e1.getDeferredEvalResult());
      //        }
      //      }
      throw new DeferredParsingException(this, sb.toString());
    } finally {
      question.getAndClearEvalResult();
      yes.getAndClearEvalResult();
      no.getAndClearEvalResult();
    }
  }

  @Override
  public Object getAndClearEvalResult() {
    Object temp = evalResult;
    evalResult = null;
    return temp;
  }

  @Override
  public boolean hasEvalResult() {
    return evalResult != null;
  }
}
