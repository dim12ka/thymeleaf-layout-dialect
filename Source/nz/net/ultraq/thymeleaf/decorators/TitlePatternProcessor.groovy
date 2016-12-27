/* 
 * Copyright 2012, Emanuel Rabina (http://www.ultraq.net.nz/)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nz.net.ultraq.thymeleaf.decorators

import nz.net.ultraq.thymeleaf.context.LayoutContext
import nz.net.ultraq.thymeleaf.expressions.ExpressionProcessor
import nz.net.ultraq.thymeleaf.models.ModelBuilder

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.thymeleaf.context.ITemplateContext
import org.thymeleaf.engine.AttributeName
import org.thymeleaf.model.IProcessableElementTag
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor
import org.thymeleaf.processor.element.IElementTagStructureHandler
import org.thymeleaf.templatemode.TemplateMode
import org.unbescape.html.HtmlEscape

/**
 * Allows for greater control of the resulting {@code <title>} element by
 * specifying a pattern with some special tokens.  This can be used to extend
 * the layout's title with the content's one, instead of simply overriding
 * it.
 * 
 * @author Emanuel Rabina
 */
class TitlePatternProcessor extends AbstractAttributeTagProcessor {

	private static final Logger logger = LoggerFactory.getLogger(TitlePatternProcessor)

	@Deprecated
	private static final String TOKEN_DECORATOR_TITLE = '$DECORATOR_TITLE'
	private static final String TOKEN_CONTENT_TITLE   = '$CONTENT_TITLE'
	private static final String TOKEN_LAYOUT_TITLE    = '$LAYOUT_TITLE'

	private static boolean warned = false

	static final String PROCESSOR_NAME = 'title-pattern'
	static final int PROCESSOR_PRECEDENCE = 1

	static final String CONTEXT_CONTENT_TITLE   = 'contentTitle'
	static final String CONTEXT_LAYOUT_TITLE    = 'layoutTitle'
	static final String CONTEXT_RESULTING_TITLE = 'resultingTitle'

	static final String CONTENT_TITLE_KEY = 'LayoutDialect::ContentTitle'
	static final String LAYOUT_TITLE_KEY  = 'LayoutDialect::LayoutTitle'

	/**
	 * Constructor, sets this processor to work on the 'title-pattern' attribute.
	 * 
	 * @param templateMode
	 * @param dialectPrefix
	 */
	TitlePatternProcessor(TemplateMode templateMode, String dialectPrefix) {

		super(templateMode, dialectPrefix, null, false, PROCESSOR_NAME, true, PROCESSOR_PRECEDENCE, true)
	}

	/**
	 * Process the {@code layout:title-pattern} directive, replaces the title text
	 * with the titles from the content and layout pages.
	 * 
	 * @param context
	 * @param tag
	 * @param attributeName
	 * @param attributeValue
	 * @param structureHandler
	 */
	@Override
	@SuppressWarnings('AssignmentToStaticFieldFromInstanceMethod')
	protected void doProcess(ITemplateContext context, IProcessableElementTag tag,
		AttributeName attributeName, String attributeValue, IElementTagStructureHandler structureHandler) {

		// Ensure this attribute is only on the <title> element
		if (tag.elementCompleteName != 'title') {
			throw new IllegalArgumentException("${attributeName} processor should only appear in a <title> element")
		}

		def titlePattern = attributeValue
		def expressionProcessor = new ExpressionProcessor(context)
		def modelFactory = context.modelFactory
		def modelBuilder = new ModelBuilder(context)

		def titleProcessor = { contextKey ->
			def titleObject = context[contextKey]
			def titleValue = null
			if (titleObject) {
				if (titleObject.titleText) {
					titleValue = HtmlEscape.unescapeHtml(expressionProcessor.processAsString(titleObject.titleText))
					if (titleObject.escape) {
						titleValue = HtmlEscape.escapeHtml5Xml(titleValue)
					}
				}
				else if (titleObject.titleModel) {
					titleValue = titleObject.titleModel
				}
			}
			return titleValue
		}

		def contentTitle = context[CONTENT_TITLE_KEY]
		def layoutTitle = context[LAYOUT_TITLE_KEY]

		if (titlePattern && titlePattern.contains(TOKEN_DECORATOR_TITLE)) {
			if (!warned) {
				logger.warn(
					'The $DECORATOR_TITLE token is deprecated and will be removed in the next major version of the layout dialect.  ' +
					'Please use the $LAYOUT_TITLE token instead to future-proof your code.  ' +
					'See https://github.com/ultraq/thymeleaf-layout-dialect/issues/95 for more information.'
				)
				warned = true
			}
		}

		// Break the title pattern up into tokens to map to their respective models
		def titleModel = modelFactory.createModel()
		if (layoutTitle && contentTitle) {
			def tokenPattern = ~/(\$(LAYOUT|DECORATOR|CONTENT)_TITLE)/
			def matcher = tokenPattern.matcher(titlePattern)

			while (matcher.find()) {
				def text = titlePattern.substring(matcher.regionStart(), matcher.start())
				if (text) {
					titleModel.add(modelFactory.createText(text))
				}
				def token = matcher.group(1)
				titleModel.addModel(token == TOKEN_LAYOUT_TITLE || token == TOKEN_DECORATOR_TITLE ? layoutTitle : contentTitle)
				matcher.region(matcher.regionStart() + text.length() + token.length(), titlePattern.length())
			}
			def remainingText = titlePattern.substring(matcher.regionStart())
			if (remainingText) {
				titleModel.add(modelFactory.createText(remainingText))
			}
		}
		else if (contentTitle) {
			titleModel.addModel(contentTitle)
		}
		else if (layoutTitle) {
			titleModel.addModel(layoutTitle)
		}

		// Build a new model based on the title pattern
//		def titleModel = modelBuilder.build {
//			'th:block' {
//				models.each { model -> add(model) }
//			}
//		}

//		def title = titlePattern && layoutTitle && contentTitle ?
//			titlePattern
//				.replace(PARAM_TITLE_LAYOUT, layoutTitle)
//				.replace(PARAM_TITLE_DECORATOR, layoutTitle)
//				.replace(PARAM_TITLE_CONTENT, contentTitle) :
//			contentTitle ?: layoutTitle ?: ''
//
//		structureHandler.setBody(title, false)
		structureHandler.setBody(titleModel, true)

		// Save the title to the layout context
		def layoutContext = LayoutContext.forContext(context)
		layoutContext << [
			(CONTEXT_CONTENT_TITLE):   contentTitle,
			(CONTEXT_LAYOUT_TITLE):    layoutTitle/*,
			(CONTEXT_RESULTING_TITLE): title*/
		]
	}
}
