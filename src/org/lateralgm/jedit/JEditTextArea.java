/*
 * Copyright (C) 2007 Quadduc <quadduc@gmail.com>
 * Copyright (C) 2008 IsmAvatar <IsmAvatar@gmail.com>
 *
 * This file is part of LateralGM.
 * LateralGM is free software and comes with ABSOLUTELY NO WARRANTY.
 * See LICENSE for details.
 * 
 * This file incorporates work covered by the following copyright and
 * permission notice: 
 * 
 *     JEditTextArea.java - jEdit's text component
 *     Copyright (C) 1999 Slava Pestov
 *     
 *     You may use and modify this package for any purpose. Redistribution is
 *     permitted, in both source and binary form, provided that this notice
 *     remains intact in all source distributions of this package.
 */

package org.lateralgm.jedit;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.Enumeration;
import java.util.Vector;

import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.EventListenerList;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.Segment;
import javax.swing.text.Utilities;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.UndoableEdit;

/**
 * jEdit's text area component. It is more suited for editing program
 * source code than JEditorPane, because it drops the unnecessary features
 * (images, variable-width lines, and so on) and adds a whole bunch of
 * useful goodies such as:
 * <ul>
 * <li>More flexible key binding scheme
 * <li>Supports macro recorders
 * <li>Rectangular selection
 * <li>Bracket highlighting
 * <li>Syntax highlighting
 * <li>Command repetition
 * <li>Block caret can be enabled
 * </ul>
 * It is also faster and doesn't have as many problems. It can be used
 * in other applications; the only other part of jEdit it depends on is
 * the syntax package.<p>
 *
 * To use it in your app, treat it like any other component, for example:
 * <pre>JEditTextArea ta = new JEditTextArea();
 * ta.setTokenMarker(new JavaTokenMarker());
 * ta.setText("public class Test2 {\n"
 *     + "    public static void main(String[] args) {\n"
 *     + "        System.out.println(\"Hello World\");\n"
 *     + "    }\n"
 *     + "}");</pre>
 *
 * @author Slava Pestov
 */
public class JEditTextArea extends JComponent
	{
	private static final long serialVersionUID = 1L;

	/**
	 * Adding components with this name to the text area will place
	 * them left of the horizontal scroll bar. In jEdit, the status
	 * bar is added this way.
	 */
	public static final String LEFT_OF_SCROLLBAR = "los";

	/** The number of lines always visible from the top or bottom */
	public int electricScroll;
	public boolean editable;
	public InputHandler inputHandler;
	/** Used to preserve the caret's column position when moving up and down lines. */
	public int magicCaret;

	public JEditTextArea()
		{
		TextAreaDefaults defaults = TextAreaDefaults.getDefaults();

		// Enable the necessary events
		enableEvents(AWTEvent.KEY_EVENT_MASK);

		// Initialize some misc. stuff
		painter = new TextAreaPainter(this,defaults);
		// Debugging code
		//		painter.addCustomHighlight(new TextAreaPainter.Highlight()
		//			{
		//				public String getToolTipText(MouseEvent evt)
		//					{
		//					return null;
		//					}
		//
		//				public void init(JEditTextArea textArea, Highlight next)
		//					{
		//					}
		//
		//				public void paintHighlight(Graphics gfx, int line, int y)
		//					{
		//					if (line != widestLine) return;
		//					gfx.setColor(new Color(192,255,192));
		//					FontMetrics fm = painter.getFontMetrics();
		//					y += TextAreaPainter.LINE_SPACING + fm.getMaxDescent();
		//					int height = painter.getLineHeight();
		//					gfx.fillRect(0,y + 2,getWidth(),height - 4);
		//					}
		//			});
		documentHandler = new DocumentHandler();
		listenerList = new EventListenerList();
		caretEvent = new MutableCaretEvent();
		lineSegment = new Segment();
		bracketPosition = -1;
		bracketLine = -1;
		blink = true;

		// Initialize the GUI
		setLayout(new ScrollLayout());
		add(CENTER,painter);
		vertical = new JScrollBar(JScrollBar.VERTICAL);
		horizontal = new JScrollBar(JScrollBar.HORIZONTAL);
		add(RIGHT,vertical);
		add(BOTTOM,horizontal);

		// Add some event listeners
		vertical.addAdjustmentListener(new AdjustHandler());
		horizontal.addAdjustmentListener(new AdjustHandler());
		painter.addComponentListener(new ComponentHandler());
		painter.addMouseListener(new MouseHandler());
		painter.addMouseMotionListener(new DragHandler());
		addFocusListener(new FocusHandler());
		addMouseWheelListener(new MouseWheelListener()
			{
				public void mouseWheelMoved(MouseWheelEvent e)
					{
					if (e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL)
						vertical.setValue(vertical.getValue() + e.getUnitsToScroll());
					}
			});
		setFocusTraversalKeysEnabled(false);

		// Load the defaults
		inputHandler = defaults.inputHandler;
		setDocument(defaults.document);
		editable = defaults.editable;
		caretVisible = defaults.caretVisible;
		electricScroll = defaults.electricScroll;

		popup = defaults.popup;

		// We don't seem to get the initial focus event?
		focusedComponent = this;
		}

	public final boolean isCaretVisible()
		{
		return blink && caretVisible;
		}

	public void setCaretVisible(boolean caretVisible)
		{
		this.caretVisible = caretVisible;
		blink = true;

		painter.invalidateSelectedLines();
		}

	/** Blinks the caret. */
	public final void blinkCaret()
		{
		blink = !blink;
		painter.invalidateSelectedLines();
		}

	/**
	 * Updates the state of the scroll bars. This should be called
	 * if the number of lines in the document changes, or when the
	 * size of the text are changes.
	 */
	public void updateScrollBars()
		{
		if (vertical != null && visibleLines != 0)
			{
			vertical.setValues(firstLine,visibleLines,0,getLineCount());
			vertical.setUnitIncrement(2);
			vertical.setBlockIncrement(visibleLines);
			}

		int width = painter.getWidth();
		if (horizontal != null && width != 0)
			{
			int w = painter.getFontMetrics().charWidth('w');
			int mw = widestLineWidth + w;
			int oho = horizontalOffset;
			if (width - horizontalOffset > mw) horizontalOffset = width - mw;
			if (horizontalOffset > 0) horizontalOffset = 0;
			if (oho != horizontalOffset) painter.repaint();
			horizontal.setValues(-horizontalOffset,width,0,mw);
			horizontal.setUnitIncrement(w);
			horizontal.setBlockIncrement(width / 2);
			}
		}

	/** Returns the line displayed at the text area's origin */
	public final int getFirstLine()
		{
		return firstLine;
		}

	/**
	 * Sets the line displayed at the text area's origin without
	 * updating the scroll bars.
	 */
	public void setFirstLine(int firstLine)
		{
		if (firstLine == this.firstLine) return;
		this.firstLine = firstLine;
		if (firstLine != vertical.getValue()) updateScrollBars();
		painter.repaint();
		}

	/** Returns the number of lines visible in this text area */
	public final int getVisibleLines()
		{
		return visibleLines;
		}

	/**
	 * Recalculates the number of visible lines. This should not
	 * be called directly.
	 */
	public final void recalculateVisibleLines()
		{
		if (painter == null) return;
		int height = painter.getHeight();
		int lineHeight = painter.getLineHeight();
		visibleLines = height / lineHeight;
		updateScrollBars();
		}

	/** Returns the horizontal offset of drawn lines. */
	public final int getHorizontalOffset()
		{
		return horizontalOffset;
		}

	/**
	 * Sets the horizontal offset of drawn lines. This can be used to
	 * implement horizontal scrolling.
	 * @param horizontalOffset offset The new horizontal offset
	 */
	public void setHorizontalOffset(int horizontalOffset)
		{
		if (horizontalOffset == this.horizontalOffset) return;
		this.horizontalOffset = horizontalOffset;
		if (horizontalOffset != horizontal.getValue()) updateScrollBars();
		painter.repaint();
		}

	/**
	 * A fast way of changing both the first line and horizontal
	 * offset.
	 * @param firstLine The new first line
	 * @param horizontalOffset The new horizontal offset
	 * @return True if any of the values were changed, false otherwise
	 */
	public boolean setOrigin(int firstLine, int horizontalOffset)
		{
		boolean changed = false;
		if (horizontalOffset != this.horizontalOffset)
			{
			this.horizontalOffset = horizontalOffset;
			changed = true;
			}

		if (firstLine != this.firstLine)
			{
			this.firstLine = firstLine;
			changed = true;
			}

		if (changed)
			{
			updateScrollBars();
			painter.repaint();
			}

		return changed;
		}

	/**
	 * Ensures that the caret is visible by scrolling the text area if
	 * necessary.
	 * @return True if scrolling was actually performed, false if the
	 * caret was already visible
	 */
	public boolean scrollToCaret()
		{
		int line = getCaretLine();
		int lineStart = getLineStartOffset(line);
		int offset = Math.max(0,Math.min(getLineLength(line) - 1,getCaretPosition() - lineStart));

		return scrollTo(line,offset);
		}

	/**
	 * Ensures that the specified line and offset is visible by scrolling
	 * the text area if necessary.
	 * @param line The line to scroll to
	 * @param offset The offset in the line to scroll to
	 * @return True if scrolling was actually performed, false if the
	 * line and offset was already visible
	 */
	public boolean scrollTo(int line, int offset)
		{
		// visibleLines == 0 before the component is realized
		// we can't do any proper scrolling then, so we have
		// this hack...
		if (visibleLines == 0)
			{
			setFirstLine(Math.max(0,line - electricScroll));
			return true;
			}

		int newFirstLine = firstLine;
		int newHorizontalOffset = horizontalOffset;

		if (line < firstLine + electricScroll)
			{
			newFirstLine = Math.max(0,line - electricScroll);
			}
		else if (line + electricScroll >= firstLine + visibleLines)
			{
			newFirstLine = (line - visibleLines) + electricScroll + 1;
			if (newFirstLine + visibleLines >= getLineCount())
				newFirstLine = getLineCount() - visibleLines;
			if (newFirstLine < 0) newFirstLine = 0;
			}

		int x = fOffsetToX(line,offset);
		int width = painter.getFontMetrics().charWidth('w');

		if (x < 0)
			{
			newHorizontalOffset = Math.min(0,horizontalOffset - x + width + 5);
			}
		else if (x + width >= painter.getWidth())
			{
			newHorizontalOffset = horizontalOffset + (painter.getWidth() - x) - width - 5;
			}

		return setOrigin(newFirstLine,newHorizontalOffset);
		}

	/**
	 * Converts a line index to a y co-ordinate.
	 * @param line The line
	 */
	public int lineToY(int line)
		{
		FontMetrics fm = painter.getFontMetrics();
		return (line - firstLine) * painter.getLineHeight()
				- (TextAreaPainter.LINE_SPACING + fm.getMaxDescent());
		}

	/**
	 * Converts a y co-ordinate to a line index.
	 * @param y The y co-ordinate
	 */
	public int yToLine(int y)
		{
		int height = painter.getLineHeight();
		return Math.max(0,Math.min(getLineCount() - 1,y / height + firstLine));
		}

	/**
	 * Converts an offset in a line into an x co-ordinate. This is a
	 * slow version that can be used any time.
	 * @param line The line
	 * @param offset The offset, from the start of the line
	 */
	public final int offsetToX(int line, int offset)
		{
		// don't use cached tokens
		painter.currentLineTokens = null;
		return fOffsetToX(line,offset);
		}

	/**
	 * Converts an offset in a line into an x co-ordinate. This is a
	 * fast version that should only be used if no changes were made
	 * to the text since the last repaint.
	 * @param line The line
	 * @param offset The offset, from the start of the line
	 */
	public int fOffsetToX(int line, int offset)
		{
		TokenMarker tokenMarker = getTokenMarker();

		/* Use painter's cached info for speed */
		FontMetrics fm = painter.getFontMetrics();

		getLineText(line,lineSegment);

		int segmentOffset = lineSegment.offset;
		int x = horizontalOffset;

		/* If syntax coloring is disabled, do simple translation */
		if (tokenMarker == null)
			{
			lineSegment.count = offset;
			return x + Utilities.getTabbedTextWidth(lineSegment,fm,x,painter,0);
			}
		/* If syntax coloring is enabled, we have to do this because
		 * tokens can vary in width */
		Token tokens;
		if (painter.currentLineIndex == line && painter.currentLineTokens != null)
			tokens = painter.currentLineTokens;
		else
			{
			painter.currentLineIndex = line;
			painter.currentLineTokens = tokenMarker.markTokens(lineSegment,line);
			tokens = painter.currentLineTokens;
			}

		//		Font defaultFont = painter.getFont();
		//		SyntaxStyle[] styles = painter.getStyles();

		for (;;)
			{
			byte id = tokens.id;
			if (id == Token.END)
				{
				return x;
				}

			//			if (id == Token.NULL)
			fm = painter.getFontMetrics();
			//			else
			//				fm = styles[id].getFontMetrics(defaultFont);

			int length = tokens.length;

			if (offset + segmentOffset < lineSegment.offset + length)
				{
				lineSegment.count = offset - (lineSegment.offset - segmentOffset);
				return x + Utilities.getTabbedTextWidth(lineSegment,fm,x,painter,0);
				}
			lineSegment.count = length;
			x += Utilities.getTabbedTextWidth(lineSegment,fm,x,painter,0);
			lineSegment.offset += length;
			tokens = tokens.next;
			}
		}

	/**
	 * Converts an x co-ordinate to an offset within a line.
	 * @param line The line
	 * @param x The x co-ordinate
	 */
	public int xToOffset(int line, int x)
		{
		TokenMarker tokenMarker = getTokenMarker();

		/* Use painter's cached info for speed */
		FontMetrics fm = painter.getFontMetrics();

		getLineText(line,lineSegment);

		char[] segmentArray = lineSegment.array;
		int segmentOffset = lineSegment.offset;
		int segmentCount = lineSegment.count;

		int width = horizontalOffset;

		if (tokenMarker == null)
			{
			for (int i = 0; i < segmentCount; i++)
				{
				char c = segmentArray[i + segmentOffset];
				int charWidth;
				if (c == '\t')
					charWidth = (int) painter.nextTabStop(width,i) - width;
				else
					charWidth = fm.charWidth(c);

				if (painter.isBlockCaretEnabled())
					{
					if (x - charWidth <= width) return i;
					}
				else
					{
					if (x - charWidth / 2 <= width) return i;
					}

				width += charWidth;
				}

			return segmentCount;
			}
		Token tokens;
		if (painter.currentLineIndex == line && painter.currentLineTokens != null)
			tokens = painter.currentLineTokens;
		else
			{
			painter.currentLineIndex = line;
			painter.currentLineTokens = tokenMarker.markTokens(lineSegment,line);
			tokens = painter.currentLineTokens;
			}

		int offset = 0;
		//		Font defaultFont = painter.getFont();
		//		SyntaxStyle[] styles = painter.getStyles();

		for (;;)
			{
			byte id = tokens.id;
			if (id == Token.END) return offset;

			//			if (id == Token.NULL)
			fm = painter.getFontMetrics();
			//			else
			//				fm = styles[id].getFontMetrics(defaultFont);

			int length = tokens.length;

			for (int i = 0; i < length; i++)
				{
				char c = segmentArray[segmentOffset + offset + i];
				int charWidth;
				if (c == '\t')
					charWidth = (int) painter.nextTabStop(width,offset + i) - width;
				else
					charWidth = fm.charWidth(c);

				if (painter.isBlockCaretEnabled())
					{
					if (x - charWidth <= width) return offset + i;
					}
				else
					{
					if (x - charWidth / 2 <= width) return offset + i;
					}

				width += charWidth;
				}

			offset += length;
			tokens = tokens.next;
			}
		}

	/**
	 * Converts a point to an offset, from the start of the text.
	 * @param x The x co-ordinate of the point
	 * @param y The y co-ordinate of the point
	 */
	public int xyToOffset(int x, int y)
		{
		int line = yToLine(y);
		int start = getLineStartOffset(line);
		return start + xToOffset(line,x);
		}

	/** Returns the document this text area is editing. */
	public final SyntaxDocument getDocument()
		{
		return document;
		}

	/**
	 * Sets the document this text area is editing.
	 * @param document The document
	 */
	public void setDocument(SyntaxDocument document)
		{
		if (this.document == document) return;
		if (this.document != null) this.document.removeDocumentListener(documentHandler);
		this.document = document;

		document.addDocumentListener(documentHandler);

		select(0,0);
		updateScrollBars();
		painter.repaint();
		}

	/**
	 * Returns the document's token marker. Equivalent to calling
	 * <code>getDocument().getTokenMarker()</code>.
	 */
	public final TokenMarker getTokenMarker()
		{
		return document.getTokenMarker();
		}

	/**
	 * Sets the document's token marker. Equivalent to caling
	 * <code>getDocument().setTokenMarker()</code>.
	 * @param tokenMarker The token marker
	 */
	public final void setTokenMarker(TokenMarker tokenMarker)
		{
		document.setTokenMarker(tokenMarker);
		}

	/**
	 * Returns the length of the document. Equivalent to calling
	 * <code>getDocument().getLength()</code>.
	 */
	public final int getDocumentLength()
		{
		return document.getLength();
		}

	/** Returns the number of lines in the document */
	public final int getLineCount()
		{
		return document.getDefaultRootElement().getElementCount();
		}

	/**
	 * Returns the line containing the specified offset.
	 * @param offset The offset
	 */
	public final int getLineOfOffset(int offset)
		{
		return document.getDefaultRootElement().getElementIndex(offset);
		}

	/**
	 * Returns the start offset of the specified line.
	 * @param line The line
	 * @return The start offset of the specified line, or -1 if the line is
	 * invalid
	 */
	public int getLineStartOffset(int line)
		{
		Element lineElement = document.getDefaultRootElement().getElement(line);
		if (lineElement == null) return -1;
		return lineElement.getStartOffset();
		}

	/**
	 * Returns the end offset of the specified line.
	 * @param line The line
	 * @return The end offset of the specified line, or -1 if the line is
	 * invalid.
	 */
	public int getLineEndOffset(int line)
		{
		Element lineElement = document.getDefaultRootElement().getElement(line);
		if (lineElement == null) return -1;
		return lineElement.getEndOffset();
		}

	public int getLineLength(int line)
		{
		Element lineElement = document.getDefaultRootElement().getElement(line);
		if (lineElement == null) return -1;
		return lineElement.getEndOffset() - lineElement.getStartOffset() - 1;
		}

	public String getText()
		{
		try
			{
			return document.getText(0,document.getLength());
			}
		catch (BadLocationException bl)
			{
			bl.printStackTrace();
			return null;
			}
		}

	public void setText(String text)
		{
		try
			{
			document.beginStructEdit();
			document.remove(0,document.getLength());
			document.insertString(0,text,null);
			}
		catch (BadLocationException bl)
			{
			bl.printStackTrace();
			}
		finally
			{
			document.endStructEdit();
			}
		}

	/**
	 * Returns the specified substring of the document.
	 * @param start The start offset
	 * @param len The length of the substring
	 * @return The substring, or null if the offsets are invalid
	 */
	public final String getText(int start, int len)
		{
		try
			{
			return document.getText(start,len);
			}
		catch (BadLocationException bl)
			{
			bl.printStackTrace();
			return null;
			}
		}

	/**
	 * Copies the specified substring of the document into a segment.
	 * If the offsets are invalid, the segment will contain a null string.
	 * @param start The start offset
	 * @param len The length of the substring
	 * @param segment The segment
	 */
	public final void getText(int start, int len, Segment segment)
		{
		try
			{
			document.getText(start,len,segment);
			}
		catch (BadLocationException bl)
			{
			bl.printStackTrace();
			segment.count = 0;
			segment.offset = 0;
			}
		}

	/**
	 * Returns the text on the specified line.
	 * @param lineIndex The line
	 * @return The text, or null if the line is invalid
	 */
	public final String getLineText(int lineIndex)
		{
		int start = getLineStartOffset(lineIndex);
		return getText(start,getLineEndOffset(lineIndex) - start - 1);
		}

	/**
	 * Copies the text on the specified line into a segment. If the line
	 * is invalid, the segment will contain a null string.
	 * @param lineIndex The line
	 */
	public final void getLineText(int lineIndex, Segment segment)
		{
		int start = getLineStartOffset(lineIndex);
		getText(start,getLineEndOffset(lineIndex) - start - 1,segment);
		}

	/**
	 * Returns the selection start offset.
	 */
	public final int getSelectionStart()
		{
		return selectionStart;
		}

	/**
	 * Returns the offset where the selection starts on the specified
	 * line.
	 */
	public int getSelectionStart(int line)
		{
		if (line == selectionStartLine)
			return selectionStart;
		else if (rectSelect)
			{
			Element map = document.getDefaultRootElement();
			int start = selectionStart - map.getElement(selectionStartLine).getStartOffset();

			Element lineElement = map.getElement(line);
			int lineStart = lineElement.getStartOffset();
			int lineEnd = lineElement.getEndOffset() - 1;
			return Math.min(lineEnd,lineStart + start);
			}
		else
			return getLineStartOffset(line);
		}

	/**
	 * Returns the selection start line.
	 */
	public final int getSelectionStartLine()
		{
		return selectionStartLine;
		}

	/**
	 * Sets the selection start. The new selection will be the new
	 * selection start and the old selection end.
	 * @param selectionStart The selection start
	 * @see #select(int,int)
	 */
	public final void setSelectionStart(int selectionStart)
		{
		select(selectionStart,selectionEnd);
		}

	/**
	 * Returns the selection end offset.
	 */
	public final int getSelectionEnd()
		{
		return selectionEnd;
		}

	/**
	 * Returns the offset where the selection ends on the specified
	 * line.
	 */
	public int getSelectionEnd(int line)
		{
		if (line == selectionEndLine)
			return selectionEnd;
		else if (rectSelect)
			{
			Element map = document.getDefaultRootElement();
			int end = selectionEnd - map.getElement(selectionEndLine).getStartOffset();

			Element lineElement = map.getElement(line);
			int lineStart = lineElement.getStartOffset();
			int lineEnd = lineElement.getEndOffset() - 1;
			return Math.min(lineEnd,lineStart + end);
			}
		else
			return getLineEndOffset(line) - 1;
		}

	/**
	 * Returns the selection end line.
	 */
	public final int getSelectionEndLine()
		{
		return selectionEndLine;
		}

	/**
	 * Sets the selection end. The new selection will be the old
	 * selection start and the bew selection end.
	 * @param selectionEnd The selection end
	 * @see #select(int,int)
	 */
	public final void setSelectionEnd(int selectionEnd)
		{
		select(selectionStart,selectionEnd);
		}

	/**
	 * Returns the caret position. This will either be the selection
	 * start or the selection end, depending on which direction the
	 * selection was made in.
	 */
	public final int getCaretPosition()
		{
		return (biasLeft ? selectionStart : selectionEnd);
		}

	public final int getCaretLine()
		{
		return (biasLeft ? selectionStartLine : selectionEndLine);
		}

	public final int getCaretColumn()
		{
		return getCaretPosition() - getLineStartOffset(getCaretLine());
		}

	/**
	 * Gets the selection bound opposite of the caret
	 * @see #getCaretPosition()
	 */
	public final int getMarkPosition()
		{
		return (biasLeft ? selectionEnd : selectionStart);
		}

	/** Gets the line of the selection bound opposite of the caret */
	public final int getMarkLine()
		{
		return (biasLeft ? selectionEndLine : selectionStartLine);
		}

	/**
	 * Sets the caret position. The new selection will consist of the
	 * caret position only (hence no text will be selected)
	 * @param caret The caret position
	 * @see #select(int,int)
	 */
	public final void setCaretPosition(int caret)
		{
		select(caret,caret);
		}

	/**
	 * Selects from the start offset to the end offset. This is the
	 * general selection method used by all other selecting methods.
	 * The caret position will be start if start &lt; end, and end
	 * if end &gt; start.
	 * @param start The start offset
	 * @param end The end offset
	 */
	public void select(int start, int end)
		{
		int newStart, newEnd;
		boolean newBias;
		if (start <= end)
			{
			newStart = start;
			newEnd = end;
			newBias = false;
			}
		else
			{
			newStart = end;
			newEnd = start;
			newBias = true;
			}

		if (newStart < 0 || newEnd > getDocumentLength())
			{
			throw new IllegalArgumentException("Bounds out of range: " + newStart + "," + newEnd);
			}

		// If the new position is the same as the old, we don't
		// do all this crap, however we still do the stuff at
		// the end (clearing magic position, scrolling)
		if (newStart != selectionStart || newEnd != selectionEnd || newBias != biasLeft)
			{
			int newStartLine = getLineOfOffset(newStart);
			int newEndLine = getLineOfOffset(newEnd);

			if (painter.isBracketHighlightEnabled())
				{
				if (bracketLine != -1) painter.invalidateLine(bracketLine);
				updateBracketHighlight(end);
				if (bracketLine != -1) painter.invalidateLine(bracketLine);
				}

			painter.invalidateLineRange(selectionStartLine,selectionEndLine);
			painter.invalidateLineRange(newStartLine,newEndLine);

			document.addUndoableEdit(new CaretUndo(selectionStart,selectionEnd));

			selectionStart = newStart;
			selectionEnd = newEnd;
			selectionStartLine = newStartLine;
			selectionEndLine = newEndLine;
			biasLeft = newBias;

			fireCaretEvent();
			}

		// When the user is typing, etc, we don't want the caret
		// to blink
		blink = true;
		caretTimer.restart();

		// Disable rectangle select if selection start = selection end
		if (selectionStart == selectionEnd) rectSelect = false;

		// Clear the `magic' caret position used by up/down
		magicCaret = -1;

		scrollToCaret();
		}

	/**
	 * Returns the selected text, or null if no selection is active.
	 */
	public final String getSelectedText()
		{
		if (selectionStart == selectionEnd) return null;

		if (rectSelect)
			{
			// Return each row of the selection on a new line

			Element map = document.getDefaultRootElement();

			int start = selectionStart - map.getElement(selectionStartLine).getStartOffset();
			int end = selectionEnd - map.getElement(selectionEndLine).getStartOffset();

			// Certain rectangles satisfy this condition...
			if (end < start)
				{
				int tmp = end;
				end = start;
				start = tmp;
				}

			StringBuffer buf = new StringBuffer();
			Segment seg = new Segment();

			for (int i = selectionStartLine; i <= selectionEndLine; i++)
				{
				Element lineElement = map.getElement(i);
				int lineStart = lineElement.getStartOffset();
				int lineEnd = lineElement.getEndOffset() - 1;
				int lineLen = lineEnd - lineStart;

				lineStart = Math.min(lineStart + start,lineEnd);
				lineLen = Math.min(end - start,lineEnd - lineStart);

				getText(lineStart,lineLen,seg);
				buf.append(seg.array,seg.offset,seg.count);

				if (i != selectionEndLine) buf.append('\n');
				}

			return buf.toString();
			}
		return getText(selectionStart,selectionEnd - selectionStart);
		}

	/**
	 * Replaces the selection with the specified text.
	 * @param selectedText The replacement text for the selection
	 */
	public void setSelectedText(String selectedText)
		{
		if (!editable)
			{
			throw new InternalError("Text component read only");
			}

		document.beginStructEdit();

		try
			{
			if (rectSelect)
				{
				Element map = document.getDefaultRootElement();

				int start = selectionStart - map.getElement(selectionStartLine).getStartOffset();
				int end = selectionEnd - map.getElement(selectionEndLine).getStartOffset();

				// Certain rectangles satisfy this condition...
				if (end < start)
					{
					int tmp = end;
					end = start;
					start = tmp;
					}

				int lastNewline = 0;
				int currNewline = 0;

				for (int i = selectionStartLine; i <= selectionEndLine; i++)
					{
					Element lineElement = map.getElement(i);
					int lineStart = lineElement.getStartOffset();
					int lineEnd = lineElement.getEndOffset() - 1;
					int rectStart = Math.min(lineEnd,lineStart + start);

					document.remove(rectStart,Math.min(lineEnd - rectStart,end - start));

					if (selectedText == null) continue;

					currNewline = selectedText.indexOf('\n',lastNewline);
					if (currNewline == -1) currNewline = selectedText.length();

					document.insertString(rectStart,selectedText.substring(lastNewline,currNewline),null);

					lastNewline = Math.min(selectedText.length(),currNewline + 1);
					}

				if (selectedText != null && currNewline != selectedText.length())
					{
					int offset = map.getElement(selectionEndLine).getEndOffset() - 1;
					document.insertString(offset,"\n",null);
					document.insertString(offset + 1,selectedText.substring(currNewline + 1),null);
					}
				}
			else
				{
				document.remove(selectionStart,selectionEnd - selectionStart);
				if (selectedText != null)
					{
					document.insertString(selectionStart,selectedText,null);
					}
				}
			}
		catch (BadLocationException bl)
			{
			bl.printStackTrace();
			throw new InternalError("Cannot replace selection");
			}
		// No matter what happends... stops us from leaving document
		// in a bad state
		finally
			{
			document.endStructEdit();
			}

		setCaretPosition(selectionEnd);
		}

	/**
	 * Similar to <code>setSelectedText()</code>, but overstrikes the
	 * appropriate number of characters if overwrite mode is enabled.
	 * @param str The string
	 * @see #setSelectedText(String)
	 * @see #isOverwriteEnabled()
	 */
	public void overwriteSetSelectedText(String str)
		{
		// Don't overstrike if there is a selection
		if (!overwrite || selectionStart != selectionEnd)
			{
			setSelectedText(str);
			return;
			}

		// Don't overstrike if we're on the end of
		// the line
		int caret = getCaretPosition();
		int caretLineEnd = getLineEndOffset(getCaretLine());
		if (caretLineEnd - caret <= str.length())
			{
			setSelectedText(str);
			return;
			}

		document.beginStructEdit();

		try
			{
			document.remove(caret,str.length());
			document.insertString(caret,str,null);
			}
		catch (BadLocationException bl)
			{
			bl.printStackTrace();
			}
		finally
			{
			document.endStructEdit();
			}
		}

	public final boolean isOverwriteEnabled()
		{
		return overwrite;
		}

	public final void setOverwriteEnabled(boolean overwrite)
		{
		this.overwrite = overwrite;
		painter.invalidateSelectedLines();
		}

	public final boolean isSelectionRectangular()
		{
		return rectSelect;
		}

	public final void setSelectionRectangular(boolean rectSelect)
		{
		this.rectSelect = rectSelect;
		painter.invalidateSelectedLines();
		}

	/**
	 * Returns the position of the highlighted bracket (the bracket
	 * matching the one before the caret)
	 */
	public final int getBracketPosition()
		{
		return bracketPosition;
		}

	/**
	 * Returns the line of the highlighted bracket (the bracket
	 * matching the one before the caret)
	 */
	public final int getBracketLine()
		{
		return bracketLine;
		}

	public final void addCaretListener(CaretListener listener)
		{
		listenerList.add(CaretListener.class,listener);
		}

	public final void removeCaretListener(CaretListener listener)
		{
		listenerList.remove(CaretListener.class,listener);
		}

	public void cut()
		{
		if (editable)
			{
			copy();
			setSelectedText("");
			}
		}

	public void copy()
		{
		if (selectionStart != selectionEnd)
			{
			Clipboard clipboard = getToolkit().getSystemClipboard();

			String selection = getSelectedText();

			int repeatCount = inputHandler.getRepeatCount();
			StringBuffer buf = new StringBuffer();
			for (int i = 0; i < repeatCount; i++)
				buf.append(selection);

			clipboard.setContents(new StringSelection(buf.toString()),null);
			}
		}

	public void paste()
		{
		if (editable)
			{
			Clipboard clipboard = getToolkit().getSystemClipboard();
			try
				{
				// The MacOS MRJ doesn't convert \r to \n,
				// so do it here
				String selection = ((String) clipboard.getContents(this).getTransferData(
						DataFlavor.stringFlavor)).replace('\r','\n');

				int repeatCount = inputHandler.getRepeatCount();
				StringBuffer buf = new StringBuffer();
				for (int i = 0; i < repeatCount; i++)
					buf.append(selection);
				selection = buf.toString();
				setSelectedText(selection);
				}
			catch (Exception e)
				{
				getToolkit().beep();
				System.err.println("Clipboard does not" + " contain a string");
				}
			}
		}

	/**
	 * Called by the AWT when this component is removed from it's parent.
	 * This stops clears the currently focused component.
	 */
	public void removeNotify()
		{
		super.removeNotify();
		if (focusedComponent == this) focusedComponent = null;
		}

	/**
	 * Forwards key events directly to the input handler.
	 * This is slightly faster than using a KeyListener
	 * because some Swing overhead is avoided.
	 */
	public void processKeyEvent(KeyEvent evt)
		{
		if (inputHandler == null) return;
		switch (evt.getID())
			{
			case KeyEvent.KEY_TYPED:
				inputHandler.keyTyped(evt);
				break;
			case KeyEvent.KEY_PRESSED:
				inputHandler.keyPressed(evt);
				break;
			case KeyEvent.KEY_RELEASED:
				inputHandler.keyReleased(evt);
				break;
			}
		}

	public int getLineWidth(int line)
		{
		Element e = document.getDefaultRootElement().getElement(line);
		return offsetToX(line,e.getEndOffset() - e.getStartOffset() - 1) - horizontalOffset;
		}

	// protected members
	protected static final String CENTER = "center";
	protected static final String RIGHT = "right";
	protected static final String BOTTOM = "bottom";

	protected static JEditTextArea focusedComponent;
	protected static Timer caretTimer;

	protected TextAreaPainter painter;
	protected JPopupMenu popup;
	protected EventListenerList listenerList;
	protected MutableCaretEvent caretEvent;

	protected boolean caretVisible;
	protected boolean blink;

	protected int firstLine;
	protected int visibleLines;
	protected int horizontalOffset;

	protected JScrollBar vertical;
	protected JScrollBar horizontal;
	protected boolean scrollBarsInitialized;

	protected SyntaxDocument document;
	protected DocumentHandler documentHandler;

	protected Segment lineSegment;

	protected int selectionStart;
	protected int selectionStartLine;
	protected int selectionEnd;
	protected int selectionEndLine;
	protected boolean biasLeft;

	protected int bracketPosition;
	protected int bracketLine;

	protected boolean overwrite;
	protected boolean rectSelect;

	protected void fireCaretEvent()
		{
		Object[] listeners = listenerList.getListenerList();
		for (int i = listeners.length - 2; i >= 0; i--)
			{
			if (listeners[i] == CaretListener.class)
				{
				((CaretListener) listeners[i + 1]).caretUpdate(caretEvent);
				}
			}
		}

	protected void updateBracketHighlight(int newCaretPosition)
		{
		if (newCaretPosition == 0)
			{
			bracketLine = -1;
			bracketPosition = -1;
			return;
			}

		try
			{
			int offset = TextUtilities.findMatchingBracket(document,newCaretPosition - 1);
			if (offset != -1)
				{
				bracketLine = getLineOfOffset(offset);
				bracketPosition = offset - getLineStartOffset(bracketLine);
				return;
				}
			}
		catch (BadLocationException bl)
			{
			bl.printStackTrace();
			}

		bracketPosition = -1;
		bracketLine = -1;
		}

	protected void documentChanged(DocumentEvent evt)
		{
		DocumentEvent.ElementChange ch = evt.getChange(document.getDefaultRootElement());

		int count;
		if (ch == null)
			count = 0;
		else
			count = ch.getChildrenAdded().length - ch.getChildrenRemoved().length;

		int line = getLineOfOffset(evt.getOffset());
		if (count == 0)
			{
			painter.invalidateLine(line);
			}
		// do magic stuff
		else if (line < firstLine)
			{
			setFirstLine(firstLine + count);
			}
		// end of magic stuff
		else
			{
			painter.invalidateLineRange(line,firstLine + visibleLines);
			}
		updateScrollBars();
		}

	class ScrollLayout implements LayoutManager
		{
		public void addLayoutComponent(String name, Component comp)
			{
			if (name.equals(CENTER))
				center = comp;
			else if (name.equals(RIGHT))
				right = comp;
			else if (name.equals(BOTTOM))
				bottom = comp;
			else if (name.equals(LEFT_OF_SCROLLBAR)) leftOfScrollBar.addElement(comp);
			}

		public void removeLayoutComponent(Component comp)
			{
			if (center == comp) center = null;
			if (right == comp) right = null;
			if (bottom == comp)
				bottom = null;
			else
				leftOfScrollBar.removeElement(comp);
			}

		public Dimension preferredLayoutSize(Container parent)
			{
			Dimension dim = new Dimension();
			Insets insets = getInsets();
			dim.width = insets.left + insets.right;
			dim.height = insets.top + insets.bottom;

			Dimension centerPref = center.getPreferredSize();
			dim.width += centerPref.width;
			dim.height += centerPref.height;
			Dimension rightPref = right.getPreferredSize();
			dim.width += rightPref.width;
			Dimension bottomPref = bottom.getPreferredSize();
			dim.height += bottomPref.height;

			return dim;
			}

		public Dimension minimumLayoutSize(Container parent)
			{
			Dimension dim = new Dimension();
			Insets insets = getInsets();
			dim.width = insets.left + insets.right;
			dim.height = insets.top + insets.bottom;

			Dimension centerPref = center.getMinimumSize();
			dim.width += centerPref.width;
			dim.height += centerPref.height;
			Dimension rightPref = right.getMinimumSize();
			dim.width += rightPref.width;
			Dimension bottomPref = bottom.getMinimumSize();
			dim.height += bottomPref.height;

			return dim;
			}

		public void layoutContainer(Container parent)
			{
			Dimension size = parent.getSize();
			Insets insets = parent.getInsets();
			int itop = insets.top;
			int ileft = insets.left;
			int ibottom = insets.bottom;
			int iright = insets.right;

			int rightWidth = right.getPreferredSize().width;
			int bottomHeight = bottom.getPreferredSize().height;
			int centerWidth = size.width - rightWidth - ileft - iright;
			int centerHeight = size.height - bottomHeight - itop - ibottom;

			center.setBounds(ileft,itop,centerWidth,centerHeight);

			right.setBounds(ileft + centerWidth,itop,rightWidth,centerHeight);

			// Lay out all status components, in order
			Enumeration<Component> status = leftOfScrollBar.elements();
			while (status.hasMoreElements())
				{
				Component comp = status.nextElement();
				Dimension dim = comp.getPreferredSize();
				comp.setBounds(ileft,itop + centerHeight,dim.width,bottomHeight);
				ileft += dim.width;
				}

			bottom.setBounds(ileft,itop + centerHeight,size.width - rightWidth - ileft - iright,
					bottomHeight);
			}

		// private members
		private Component center;
		private Component right;
		private Component bottom;
		private Vector<Component> leftOfScrollBar = new Vector<Component>();
		}

	static class CaretBlinker implements ActionListener
		{
		public void actionPerformed(ActionEvent evt)
			{
			if (focusedComponent != null && focusedComponent.hasFocus()) focusedComponent.blinkCaret();
			}
		}

	class MutableCaretEvent extends CaretEvent
		{
		private static final long serialVersionUID = 1L;

		MutableCaretEvent()
			{
			super(JEditTextArea.this);
			}

		public int getDot()
			{
			return getCaretPosition();
			}

		public int getMark()
			{
			return getMarkPosition();
			}
		}

	class AdjustHandler implements AdjustmentListener
		{
		public void adjustmentValueChanged(final AdjustmentEvent evt)
			{
			if (!scrollBarsInitialized) return;

			// If this is not done, mousePressed events accumilate
			// and the result is that scrolling doesn't stop after
			// the mouse is released
			SwingUtilities.invokeLater(new Runnable()
				{
					public void run()
						{
						if (evt.getAdjustable() == vertical)
							setFirstLine(vertical.getValue());
						else
							setHorizontalOffset(-horizontal.getValue());
						}
				});
			}
		}

	class ComponentHandler extends ComponentAdapter
		{
		public void componentResized(ComponentEvent evt)
			{
			recalculateVisibleLines();
			scrollBarsInitialized = true;
			}
		}

	protected int widestLine = 0;
	protected int widestLineWidth = 0;

	class DocumentHandler implements DocumentListener
		{
		public void insertUpdate(DocumentEvent evt)
			{
			Element re = document.getDefaultRootElement();
			DocumentEvent.ElementChange ch = evt.getChange(re);
			if (ch != null)
				{
				int i = ch.getIndex();
				Element[] ca = ch.getChildrenAdded();
				Element[] cr = ch.getChildrenRemoved();
				if (widestLine > i + cr.length)
					{
					widestLine += ca.length - cr.length;
					findWidestLine(i,i + ca.length);
					}
				else if (widestLine >= i)
					{
					widestLine = -1;
					int pwlw = widestLineWidth;
					widestLineWidth = -1;
					findWidestLine(i,i + ca.length);
					if (widestLineWidth < pwlw)
						{
						findWidestLine(0,i);
						findWidestLine(i + ca.length,re.getElementCount());
						}
					}
				else
					findWidestLine(i,i + ca.length);
				}
			else
				{
				int pos = evt.getOffset();
				int line = re.getElementIndex(pos);
				int lineWidth = getLineWidth(line);
				if (widestLine == line)
					widestLineWidth = lineWidth;
				else if (lineWidth > widestLineWidth)
					{
					widestLine = line;
					widestLineWidth = lineWidth;
					}
				}

			documentChanged(evt);

			int offset = evt.getOffset();
			int length = evt.getLength();

			int newStart;
			int newEnd;

			if (selectionStart > offset || (selectionStart == selectionEnd && selectionStart == offset))
				newStart = selectionStart + length;
			else
				newStart = selectionStart;

			if (selectionEnd >= offset)
				newEnd = selectionEnd + length;
			else
				newEnd = selectionEnd;

			select(newStart,newEnd);
			}

		public void removeUpdate(DocumentEvent evt)
			{
			Element re = document.getDefaultRootElement();
			DocumentEvent.ElementChange ch = evt.getChange(re);
			if (ch != null)
				{
				int i = ch.getIndex();
				Element[] cr = ch.getChildrenRemoved();
				if (widestLine > i + cr.length)
					widestLine -= cr.length;
				else if (widestLine >= i)
					{
					widestLine = -1;
					widestLineWidth = -1;
					findWidestLine();
					}
				}
			else
				{
				int pos = evt.getOffset();
				int line = re.getElementIndex(pos);
				if (widestLine == line)
					{
					widestLineWidth = getLineWidth(line);
					findWidestLine();
					}
				}

			documentChanged(evt);

			int offset = evt.getOffset();
			int length = evt.getLength();

			int newStart;
			int newEnd;

			if (selectionStart > offset)
				{
				if (selectionStart > offset + length)
					newStart = selectionStart - length;
				else
					newStart = offset;
				}
			else
				newStart = selectionStart;

			if (selectionEnd > offset)
				{
				if (selectionEnd > offset + length)
					newEnd = selectionEnd - length;
				else
					newEnd = offset;
				}
			else
				newEnd = selectionEnd;

			select(newStart,newEnd);
			}

		public void changedUpdate(DocumentEvent evt)
			{ //Unused
			}

		protected void findWidestLine()
			{
			Element re = document.getDefaultRootElement();
			findWidestLine(0,re.getElementCount());
			}

		protected void findWidestLine(int from, int to)
			{
			for (int i = from; i < to; i++)
				{
				int w = getLineWidth(i);
				if (w >= widestLineWidth)
					{
					widestLine = i;
					widestLineWidth = w;
					}
				}
			}
		}

	class DragHandler implements MouseMotionListener
		{
		public void mouseDragged(MouseEvent evt)
			{
			if (popup != null && popup.isVisible()) return;

			setSelectionRectangular((evt.getModifiers() & InputEvent.CTRL_MASK) != 0);
			select(getMarkPosition(),xyToOffset(evt.getX(),evt.getY()));
			}

		public void mouseMoved(MouseEvent evt)
			{ //Unused
			}
		}

	class FocusHandler implements FocusListener
		{
		public void focusGained(FocusEvent evt)
			{
			setCaretVisible(true);
			focusedComponent = JEditTextArea.this;
			}

		public void focusLost(FocusEvent evt)
			{
			setCaretVisible(false);
			focusedComponent = null;
			}
		}

	class MouseHandler extends MouseAdapter
		{
		public void mousePressed(MouseEvent evt)
			{
			requestFocus();
			// Focus events not fired sometimes?
			setCaretVisible(true);
			focusedComponent = JEditTextArea.this;
			if (evt.getModifiers() == 0) return;
			if ((evt.getModifiers() & InputEvent.BUTTON3_MASK) != 0 && popup != null)
				{
				popup.show(painter,evt.getX(),evt.getY());
				return;
				}

			int line = yToLine(evt.getY());
			int offset = xToOffset(line,evt.getX());
			int dot = getLineStartOffset(line) + offset;

			switch (evt.getClickCount())
				{
				case 1:
					doSingleClick(evt,dot);
					break;
				case 2:
					doDoubleClick(line,offset,dot);
					break;
				case 3:
					doTripleClick(line);
					break;
				}
			}

		private void doSingleClick(MouseEvent evt, int dot)
			{
			if ((evt.getModifiers() & InputEvent.SHIFT_MASK) != 0)
				{
				rectSelect = (evt.getModifiers() & InputEvent.CTRL_MASK) != 0;
				select(getMarkPosition(),dot);
				}
			else
				setCaretPosition(dot);
			}

		private void doDoubleClick(int line, int offset, int dot)
			{
			// Ignore empty lines
			if (getLineLength(line) == 0) return;

			try
				{
				int bracket = TextUtilities.findMatchingBracket(document,Math.max(0,dot - 1));
				if (bracket != -1)
					{
					int mark = getMarkPosition();
					// Hack
					if (bracket > mark)
						{
						bracket++;
						mark--;
						}
					select(mark,bracket);
					return;
					}
				}
			catch (BadLocationException bl)
				{
				bl.printStackTrace();
				}

			// Ok, it's not a bracket... select the word
			String lineText = getLineText(line);
			char ch = lineText.charAt(Math.max(0,offset - 1));

			String noWordSep = (String) document.getProperty("noWordSep");
			if (noWordSep == null) noWordSep = "";

			// If the user clicked on a non-letter char,
			// we select the surrounding non-letters
			boolean selectNoLetter = (!Character.isLetterOrDigit(ch) && noWordSep.indexOf(ch) == -1);

			int wordStart = 0;

			for (int i = offset - 1; i >= 0; i--)
				{
				ch = lineText.charAt(i);
				if (selectNoLetter ^ (!Character.isLetterOrDigit(ch) && noWordSep.indexOf(ch) == -1))
					{
					wordStart = i + 1;
					break;
					}
				}

			int wordEnd = lineText.length();
			for (int i = offset; i < lineText.length(); i++)
				{
				ch = lineText.charAt(i);
				if (selectNoLetter ^ (!Character.isLetterOrDigit(ch) && noWordSep.indexOf(ch) == -1))
					{
					wordEnd = i;
					break;
					}
				}

			int lineStart = getLineStartOffset(line);
			select(lineStart + wordStart,lineStart + wordEnd);

			/*
			 String lineText = getLineText(line);
			 String noWordSep = (String)document.getProperty("noWordSep");
			 int wordStart = TextUtilities.findWordStart(lineText,offset,noWordSep);
			 int wordEnd = TextUtilities.findWordEnd(lineText,offset,noWordSep);

			 int lineStart = getLineStartOffset(line);
			 select(lineStart + wordStart,lineStart + wordEnd);
			 */
			}

		private void doTripleClick(int line)
			{
			select(getLineStartOffset(line),getLineEndOffset(line) - 1);
			}
		}

	class CaretUndo extends AbstractUndoableEdit
		{
		private static final long serialVersionUID = 1L;
		private int start;
		private int end;

		CaretUndo(int start, int end)
			{
			this.start = start;
			this.end = end;
			}

		public boolean isSignificant()
			{
			return false;
			}

		public String getPresentationName()
			{
			return "caret move";
			}

		public void undo()
			{
			super.undo();

			select(start,end);
			}

		public void redo()
			{
			super.redo();

			select(start,end);
			}

		public boolean addEdit(UndoableEdit edit)
			{
			if (edit instanceof CaretUndo)
				{
				CaretUndo cedit = (CaretUndo) edit;
				start = cedit.start;
				end = cedit.end;
				cedit.die();

				return true;
				}
			return false;
			}
		}

	static
		{
		caretTimer = new Timer(500,new CaretBlinker());
		caretTimer.setInitialDelay(500);
		caretTimer.start();
		}
	}
