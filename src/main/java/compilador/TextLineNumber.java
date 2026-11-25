package compilador;

import javax.swing.BorderFactory;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Element;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * Requisito 4 - Componente de numeração de linhas do editor.
 *
 * É um JTextArea NÃO editável usado como "row header" do JScrollPane do editor.
 * Como o row header pertence ao próprio JScrollPane, a rolagem vertical é
 * sincronizada automaticamente com a área de texto. A quantidade de números é
 * mantida em dia por um DocumentListener registrado no documento do editor.
 */
public class TextLineNumber extends JTextArea implements DocumentListener {

    private static final long serialVersionUID = 1L;

    private final JTextArea editor;

    /**
     * Quando verdadeiro, a numeração não para na última linha com texto: ela
     * continua até o fim da área visível, numerando também as linhas vazias.
     */
    private final boolean numerarLinhasVazias;

    /** Numeração colada ao conteúdo: para na última linha com texto. */
    public TextLineNumber(JTextArea editor) {
        this(editor, false);
    }

    /**
     * @param numerarLinhasVazias se true, numera a área visível inteira, mesmo
     *                            as linhas ainda não preenchidas.
     */
    public TextLineNumber(JTextArea editor, boolean numerarLinhasVazias) {
        this.editor = editor;
        this.numerarLinhasVazias = numerarLinhasVazias;

        // A numeração não pode ser editada nem receber o foco do teclado
        setEditable(false);
        setFocusable(false);

        // Mesma fonte do editor: garante que a altura das linhas coincida
        setFont(editor.getFont());
        setBackground(new Color(235, 235, 235));
        setForeground(new Color(90, 90, 90));

        // O recuo superior é lido do próprio editor, e não fixado aqui: assim os
        // números continuam alinhados com o texto mesmo que o Look and Feel do
        // sistema altere as bordas do JTextArea.
        int recuoSuperior = editor.getInsets().top;
        setMargin(new Insets(0, 0, 0, 0));
        setBorder(BorderFactory.createCompoundBorder(
                // Linha separadora à direita, entre os números e o texto
                BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(180, 180, 180)),
                BorderFactory.createEmptyBorder(recuoSuperior, 5, 0, 5)));

        editor.getDocument().addDocumentListener(this);

        // Ao arrastar o divisor do JSplitPane a altura visível muda, e a
        // quantidade de linhas a numerar muda junto
        if (numerarLinhasVazias) {
            editor.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent evento) {
                    atualizarNumeracao();
                }
            });
        }
        atualizarNumeracao();
    }

    /**
     * Quantas linhas numerar: as do documento e, no modo de preenchimento,
     * no mínimo as que cabem na altura visível.
     */
    private int totalDeLinhas() {
        // Cada filho do elemento raiz de um PlainDocument é uma linha
        Element raiz = editor.getDocument().getDefaultRootElement();
        int doDocumento = raiz.getElementCount();

        if (!numerarLinhasVazias) {
            return doDocumento;
        }

        FontMetrics medidas = getFontMetrics(getFont());
        int alturaLinha = medidas.getHeight();
        if (alturaLinha <= 0) {
            return doDocumento;
        }
        int alturaVisivel = editor.getHeight() - getInsets().top;
        int cabemNaTela = alturaVisivel / alturaLinha;
        return Math.max(doDocumento, cabemNaTela);
    }

    /**
     * Reconstrói a lista de números (1, 2, 3, ...).
     */
    private void atualizarNumeracao() {
        int totalLinhas = totalDeLinhas();

        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= totalLinhas; i++) {
            sb.append(i);
            if (i < totalLinhas) {
                sb.append('\n');
            }
        }
        setText(sb.toString());

        // A largura acompanha o maior número exibido (10, 100, 1000, ...)
        setColumns(String.valueOf(totalLinhas).length());
    }

    /**
     * O documento do editor está bloqueado durante a notificação, por isso a
     * atualização é agendada para o fim da fila de eventos do Swing.
     */
    private void agendarAtualizacao() {
        SwingUtilities.invokeLater(this::atualizarNumeracao);
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        agendarAtualizacao();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        agendarAtualizacao();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        agendarAtualizacao();
    }
}
