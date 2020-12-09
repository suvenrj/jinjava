package com.hubspot.jinjava.lib.tag.eager;

import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.interpret.TemplateSyntaxException;
import com.hubspot.jinjava.lib.tag.PrintTag;
import com.hubspot.jinjava.tree.parse.TagToken;
import com.hubspot.jinjava.util.ChunkResolver;
import com.hubspot.jinjava.util.LengthLimitingStringJoiner;
import com.hubspot.jinjava.util.WhitespaceUtils;
import org.apache.commons.lang3.StringUtils;

public class EagerPrintTag extends EagerStateChangingTag<PrintTag> {

  public EagerPrintTag() {
    super(new PrintTag());
  }

  public EagerPrintTag(PrintTag printTag) {
    super(printTag);
  }

  @Override
  public String getEagerTagImage(TagToken tagToken, JinjavaInterpreter interpreter) {
    String expr = tagToken.getHelpers();
    if (StringUtils.isBlank(expr)) {
      throw new TemplateSyntaxException(
        interpreter,
        tagToken.getImage(),
        "Tag 'print' expects expression"
      );
    }
    return interpretExpression(expr, tagToken, interpreter, true);
  }

  /**
   * Interprets the expression, which may depend on deferred values.
   * If the expression can be entirely evaluated, return the result only if
   * {@code includeExpressionResult} is true.
   * When the expression depends on deferred values, then reconstruct the tag.
   * @param expr Expression to interpret.
   * @param tagToken TagToken which is calling the expression.
   * @param interpreter The Jinjava interpreter.
   * @param includeExpressionResult Whether to include the result of the expression in
   *                                the output.
   * @return The result of the expression, if requested. OR a reconstruction of the calling tag.
   */
  public static String interpretExpression(
    String expr,
    TagToken tagToken,
    JinjavaInterpreter interpreter,
    boolean includeExpressionResult
  ) {
    ChunkResolver chunkResolver = new ChunkResolver(expr, tagToken, interpreter);
    EagerStringResult resolvedExpression = executeInChildContext(
      eagerInterpreter -> chunkResolver.resolveChunks(),
      interpreter,
      true
    );
    LengthLimitingStringJoiner joiner = new LengthLimitingStringJoiner(
      interpreter.getConfig().getMaxOutputSize(),
      " "
    );
    joiner
      .add(tagToken.getSymbols().getExpressionStartWithTag())
      .add(tagToken.getTagName())
      .add(resolvedExpression.getResult())
      .add(tagToken.getSymbols().getExpressionEndWithTag());
    StringBuilder prefixToPreserveState = new StringBuilder(
      interpreter.getContext().isDeferredExecutionMode()
        ? resolvedExpression.getPrefixToPreserveState()
        : ""
    );
    if (chunkResolver.getDeferredWords().isEmpty()) {
      // Possible macro/set tag in front of this one.
      return (
        prefixToPreserveState.toString() +
        (
          includeExpressionResult
            ? wrapInRawIfNeeded(
              WhitespaceUtils.unquote(resolvedExpression.getResult()),
              interpreter
            )
            : ""
        )
      );
    }
    prefixToPreserveState.append(
      reconstructFromContextBeforeDeferring(chunkResolver.getDeferredWords(), interpreter)
    );
    interpreter
      .getContext()
      .handleEagerToken(
        new EagerToken(
          new TagToken(
            joiner.toString(),
            tagToken.getLineNumber(),
            tagToken.getStartPosition(),
            tagToken.getSymbols()
          ),
          chunkResolver.getDeferredWords()
        )
      );
    // Possible set tag in front of this one.
    return wrapInAutoEscapeIfNeeded(
      prefixToPreserveState.toString() + joiner.toString(),
      interpreter
    );
  }
}
