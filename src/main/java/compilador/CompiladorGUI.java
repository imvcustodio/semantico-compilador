package compilador;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Classe principal da interface gráfica do compilador.
 * Implementa todos os 15 requisitos do trabalho final - parte 3.
 */
public class CompiladorGUI extends JFrame {

    private static final long serialVersionUID = 1L;

    /**
     * Fonte lógica MONOSPACED: existe em toda implementação Java (Windows,
     * Linux e macOS), o que mantém o alinhamento da numeração de linhas
     * idêntico em qualquer sistema.
     */
    private static final Font FONTE_EDITOR = new Font(Font.MONOSPACED, Font.PLAIN, 14);

    /**
     * Quebra de linha usada ao gravar. É sempre CRLF, e não o separador do
     * sistema: CRLF é lido corretamente pelo Notepad do Windows, pelo gedit do
     * Linux e pelo TextEdit do macOS, enquanto um arquivo só com LF apareceria
     * como uma única linha no Notepad. Assim o arquivo gravado em um sistema
     * abre corretamente em qualquer outro.
     */
    private static final String QUEBRA_DE_LINHA = "\r\n";

    /** Marca de ordem de bytes do UTF-8 (EF BB BF). */
    private static final byte[] BOM_UTF8 = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** Recuo interno do texto, igual no editor e na coluna de numeração. */
    static final int RECUO_SUPERIOR = 2;

    /** Colunas da lista de tokens: linha, classe e lexema. */
    private static final String FORMATO_LINHA = "%-7s%-19s%s\n";

    /** Requisito 1: dimensões fixas da janela. */
    private static final int LARGURA_JANELA = 1500;
    private static final int ALTURA_JANELA = 800;

    // Componentes principais da interface
    private JTextArea editorTextArea;
    private JTextArea messageTextArea;
    private JLabel statusBar;
    private JToolBar toolBar;

    /**
     * Estado do arquivo atual: null significa "arquivo novo", ainda sem caminho
     * em disco; diferente de null significa "arquivo já existente".
     */
    private File currentFile = null;

    public CompiladorGUI() {
        // 1. A interface do compilador deve ter tamanho fixo 1500x800
        setSize(LARGURA_JANELA, ALTURA_JANELA);
        // Não deve ser possível alterar o tamanho da janela
        setResizable(false);
        travarTamanho();
        // Minimizar e fechar continuam funcionando normalmente
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("Interface do Compilador");
        setLocationRelativeTo(null); // Centraliza a janela na tela
        garantirJanelaVisivel();

        // 2. Layout geral do frame
        setLayout(new BorderLayout());

        // Inicialização dos componentes
        initToolbar();
        initAreasDeTexto();
        initStatusBar();
        initAtalhosGlobais();
    }

    /**
     * Requisito 1: mantém a janela em 1500x800 em qualquer situação.
     *
     * Só o setResizable(false) não basta: ele desabilita o arrasto das bordas e
     * o botão de maximizar, mas a janela ainda pode ser maximizada por atalho do
     * sistema ou pelo encaixe automático de janelas, e nesse caso não voltaria
     * sozinha ao tamanho original. Estes dois ouvintes desfazem qualquer
     * alteração de tamanho (menos minimizar), seja qual for o sistema operacional.
     */
    private void travarTamanho() {
        // Desfaz maximizar, mas deixa minimizar (ICONIFIED) funcionar: o teste
        // antigo "estado != NORMAL" também pegava a janela minimizada e a
        // restaurava na mesma hora, por isso ela não minimizava.
        addWindowStateListener(evento -> {
            int estado = getExtendedState();
            boolean minimizada = (estado & Frame.ICONIFIED) != 0;
            boolean maximizada = (estado & Frame.MAXIMIZED_BOTH) != 0;
            if (!minimizada && maximizada) {
                setExtendedState(Frame.NORMAL);
                setSize(LARGURA_JANELA, ALTURA_JANELA);
            }
        });
        // Desfaz qualquer outra alteração de tamanho
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent evento) {
                // Janela minimizada: o sistema pode informar outro tamanho; não mexer
                if ((getExtendedState() & Frame.ICONIFIED) != 0) {
                    return;
                }
                if (getWidth() != LARGURA_JANELA || getHeight() != ALTURA_JANELA) {
                    setSize(LARGURA_JANELA, ALTURA_JANELA);
                }
            }
        });
    }

    /**
     * Em telas menores que 1500x800 (ou com escala de exibição ampliada, comum
     * no Windows), centralizar deixaria parte da janela fora do monitor,
     * inclusive a barra de título. Este ajuste garante que o canto superior
     * esquerdo nunca fique com coordenada negativa.
     */
    private void garantirJanelaVisivel() {
        Point posicao = getLocation();
        setLocation(Math.max(0, posicao.x), Math.max(0, posicao.y));
    }

    // ------------------------------------------------------------------
    // Construção da interface
    // ------------------------------------------------------------------

    /**
     * 2 e 9. Barra de ferramentas (WEST), com largura fixa de 150px.
     */
    private void initToolbar() {
        toolBar = new JToolBar();
        toolBar.setOrientation(SwingConstants.VERTICAL);
        // Largura 150; a altura é definida pelo BorderLayout (altura do frame)
        toolBar.setPreferredSize(new Dimension(150, 0));
        toolBar.setFloatable(false); // Fixa, para não soltar da janela

        // 9. Botões na ordem exata exigida, com rótulo = nome + tecla de atalho
        JButton btnNovo     = createToolbarButton("novo [ctrl-n]",     new AcaoIcon(AcaoIcon.NOVO));
        JButton btnAbrir    = createToolbarButton("abrir [ctrl-o]",    new AcaoIcon(AcaoIcon.ABRIR));
        JButton btnSalvar   = createToolbarButton("salvar [ctrl-s]",   new AcaoIcon(AcaoIcon.SALVAR));
        JButton btnCopiar   = createToolbarButton("copiar [ctrl-c]",   new AcaoIcon(AcaoIcon.COPIAR));
        JButton btnColar    = createToolbarButton("colar [ctrl-v]",    new AcaoIcon(AcaoIcon.COLAR));
        JButton btnRecortar = createToolbarButton("recortar [ctrl-x]", new AcaoIcon(AcaoIcon.RECORTAR));
        JButton btnCompilar = createToolbarButton("compilar [F7]",     new AcaoIcon(AcaoIcon.COMPILAR));
        JButton btnEquipe   = createToolbarButton("equipe [F1]",       new AcaoIcon(AcaoIcon.EQUIPE));

        // Ações dos botões
        btnNovo.addActionListener(e -> acaoNovo());
        btnAbrir.addActionListener(e -> acaoAbrir());
        btnSalvar.addActionListener(e -> acaoSalvar());
        btnCopiar.addActionListener(e -> acaoCopiar());
        btnColar.addActionListener(e -> acaoColar());
        btnRecortar.addActionListener(e -> acaoRecortar());
        btnCompilar.addActionListener(e -> acaoCompilar());
        btnEquipe.addActionListener(e -> acaoEquipe());

        toolBar.add(btnNovo);
        toolBar.add(btnAbrir);
        toolBar.add(btnSalvar);
        toolBar.add(btnCopiar);
        toolBar.add(btnColar);
        toolBar.add(btnRecortar);
        toolBar.add(btnCompilar);
        toolBar.add(btnEquipe);

        add(toolBar, BorderLayout.WEST);
    }

    /**
     * 9. Cria um botão com tamanho fixo (todos iguais), ícone em cima e texto embaixo.
     */
    private JButton createToolbarButton(String texto, Icon icone) {
        JButton btn = new JButton(texto, icone);
        btn.setVerticalTextPosition(SwingConstants.BOTTOM);
        btn.setHorizontalTextPosition(SwingConstants.CENTER);

        // Os três tamanhos iguais garantem que o BoxLayout da JToolBar
        // não estique nem encolha nenhum botão
        Dimension dimensao = new Dimension(140, 85);
        btn.setMinimumSize(dimensao);
        btn.setMaximumSize(dimensao);
        btn.setPreferredSize(dimensao);
        btn.setFocusPainted(false);
        return btn;
    }

    /**
     * 2 a 7. Editor (em cima) e área de mensagens (embaixo), dentro de um
     * JSplitPane vertical posicionado no CENTER.
     */
    private void initAreasDeTexto() {
        // ---------- Editor ----------
        editorTextArea = new JTextArea();
        editorTextArea.setFont(FONTE_EDITOR);
        editorTextArea.setTabSize(4);
        // Margem zerada e borda explícita: alguns Look and Feels (Nimbus, GTK)
        // instalam bordas próprias no JTextArea, o que deslocaria o texto em
        // relação à numeração. Fixando os dois, o alinhamento não muda de SO.
        editorTextArea.setMargin(new Insets(0, 0, 0, 0));
        editorTextArea.setBorder(BorderFactory.createEmptyBorder(RECUO_SUPERIOR, 5, 0, 2));
        // 5. Sem quebra automática, para a barra horizontal fazer sentido
        editorTextArea.setLineWrap(false);

        JScrollPane editorScrollPane = new JScrollPane(editorTextArea);
        // 5. Barras de rolagem sempre visíveis, mesmo sem texto
        editorScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        editorScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

        // 5. Barras sempre desenhadas, em qualquer sistema
        aplicarBarrasSempreVisiveis(editorScrollPane);

        // 4. Numeração de linhas à esquerda, como row header do JScrollPane.
        // O row header rola junto com o texto automaticamente. A numeração
        // cobre toda a altura visível, numerando também as linhas ainda não
        // preenchidas, e cresce junto com o texto quando ele passa da tela.
        editorScrollPane.setRowHeaderView(new TextLineNumber(editorTextArea, true));

        // ---------- Área de mensagens ----------
        messageTextArea = new JTextArea();
        messageTextArea.setFont(FONTE_EDITOR);
        messageTextArea.setMargin(new Insets(0, 0, 0, 0));
        messageTextArea.setBorder(BorderFactory.createEmptyBorder(RECUO_SUPERIOR, 5, 0, 2));
        messageTextArea.setLineWrap(false);
        // 6. Não deve ser possível editar texto na área para mensagens
        messageTextArea.setEditable(false);

        JScrollPane messageScrollPane = new JScrollPane(messageTextArea);
        // 7. Barras de rolagem sempre visíveis, mesmo sem mensagens
        messageScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        messageScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

        // 7. Barras sempre desenhadas, em qualquer sistema
        aplicarBarrasSempreVisiveis(messageScrollPane);

        // 3. O divisor permite alterar a altura do editor e da área de mensagens
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                editorScrollPane, messageScrollPane);
        splitPane.setOneTouchExpandable(true);
        splitPane.setResizeWeight(0.7);
        // A posição inicial do divisor vem do tamanho preferido do componente de cima
        editorScrollPane.setPreferredSize(new Dimension(0, 500));
        messageScrollPane.setPreferredSize(new Dimension(0, 200));

        add(splitPane, BorderLayout.CENTER);
    }

    /**
     * Requisitos 5 e 7: as barras de rolagem devem estar sempre visíveis.
     *
     * A política SCROLLBAR_ALWAYS reserva o espaço das barras, mas quem decide
     * se elas são realmente desenhadas é o Look and Feel: no macOS, por exemplo,
     * a barra do sistema fica transparente e só aparece durante a rolagem.
     * Instalando uma aparência própria nas quatro barras, elas são desenhadas do
     * mesmo jeito e a qualquer momento, em Windows, Linux ou macOS.
     */
    private void aplicarBarrasSempreVisiveis(JScrollPane painel) {
        painel.getVerticalScrollBar().setUI(new BarraDeRolagemUI());
        painel.getHorizontalScrollBar().setUI(new BarraDeRolagemUI());
        painel.getVerticalScrollBar().setOpaque(true);
        painel.getHorizontalScrollBar().setOpaque(true);
    }

    /**
     * Aparência fixa das barras de rolagem, com trilho e cursor sempre pintados.
     * Estende BasicScrollBarUI, que existe em qualquer implementação Java e não
     * depende das cores instaladas pelo Look and Feel do sistema.
     */
    private static class BarraDeRolagemUI extends BasicScrollBarUI {

        @Override
        protected void configureScrollBarColors() {
            trackColor = new Color(238, 238, 238);
            trackHighlightColor = new Color(238, 238, 238);
            thumbColor = new Color(170, 170, 170);
            thumbLightShadowColor = new Color(150, 150, 150);
            thumbHighlightColor = new Color(200, 200, 200);
            thumbDarkShadowColor = new Color(120, 120, 120);
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trilho) {
            g.setColor(trackColor);
            g.fillRect(trilho.x, trilho.y, trilho.width, trilho.height);
            g.setColor(new Color(205, 205, 205));
            g.drawRect(trilho.x, trilho.y, trilho.width - 1, trilho.height - 1);
        }

        /**
         * O BasicScrollBarUI não chama paintThumb quando não há o que rolar.
         * Aqui o desenho é sempre feito: sem conteúdo a rolar, o cursor ocupa o
         * trilho inteiro, e a barra fica igualmente visível a qualquer momento.
         */
        @Override
        public void paint(Graphics g, JComponent c) {
            Rectangle trilho = getTrackBounds();
            paintTrack(g, c, trilho);

            Rectangle cursor = getThumbBounds();
            if (cursor == null || cursor.isEmpty()) {
                cursor = trilho;
            }
            paintThumb(g, c, cursor);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle cursor) {
            if (cursor.isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(thumbColor);
            g2.fillRoundRect(cursor.x + 2, cursor.y + 2, cursor.width - 4, cursor.height - 4, 6, 6);
            g2.setColor(thumbDarkShadowColor);
            g2.drawRoundRect(cursor.x + 2, cursor.y + 2, cursor.width - 4, cursor.height - 4, 6, 6);
            g2.dispose();
        }
    }

    /**
     * 2 e 8. Barra de status (SOUTH) com altura fixa de 25px.
     */
    private void initStatusBar() {
        statusBar = new JLabel("");
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLoweredBevelBorder(),
                BorderFactory.createEmptyBorder(0, 6, 0, 6)));
        // Altura 25; a largura é esticada pelo BorderLayout
        statusBar.setPreferredSize(new Dimension(0, 25));
        add(statusBar, BorderLayout.SOUTH);
    }

    /**
     * Requisito 8: exibe a pasta e o nome do arquivo na barra de status.
     *
     * Caminhos muito longos não cabem na largura da barra, e o JLabel corta
     * pela direita — justamente onde está o nome do arquivo. Por isso, quando
     * não cabe, são os diretórios do início que dão lugar a reticências,
     * preservando o nome. O caminho completo continua acessível na dica que
     * aparece ao passar o mouse.
     *
     * @param arquivo o arquivo atual, ou null para esvaziar a barra.
     */
    private void definirStatus(File arquivo) {
        if (arquivo == null) {
            statusBar.setText("");
            statusBar.setToolTipText(null);
            return;
        }
        String caminho = arquivo.getAbsolutePath();
        statusBar.setToolTipText(caminho);
        statusBar.setText(ajustarLargura(caminho));
    }

    /**
     * Devolve o caminho como está, se couber na barra; caso contrário, troca os
     * diretórios iniciais por reticências até que o restante caiba.
     */
    private String ajustarLargura(String caminho) {
        FontMetrics medidas = statusBar.getFontMetrics(statusBar.getFont());
        Insets recuos = statusBar.getInsets();
        int larguraBarra = statusBar.getWidth() > 0 ? statusBar.getWidth() : LARGURA_JANELA;
        int disponivel = larguraBarra - recuos.left - recuos.right;

        if (medidas.stringWidth(caminho) <= disponivel) {
            return caminho;
        }

        String[] partes = caminho.split(java.util.regex.Pattern.quote(File.separator));
        for (int inicio = 1; inicio < partes.length; inicio++) {
            StringBuilder sb = new StringBuilder("...");
            for (int i = inicio; i < partes.length; i++) {
                sb.append(File.separator).append(partes[i]);
            }
            if (medidas.stringWidth(sb.toString()) <= disponivel) {
                return sb.toString();
            }
        }
        // Último recurso: pelo menos o nome do arquivo
        return "..." + File.separator + partes[partes.length - 1];
    }

    /**
     * 9. Atalhos de teclado registrados no root pane (WHEN_IN_FOCUSED_WINDOW),
     * para funcionarem em qualquer ponto da janela.
     */
    private void initAtalhosGlobais() {
        mapearAtalho("novo",     KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK, this::acaoNovo);
        mapearAtalho("abrir",    KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK, this::acaoAbrir);
        mapearAtalho("salvar",   KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK, this::acaoSalvar);
        // Ctrl+C/V/X já são nativos do JTextArea quando ele tem o foco; estes
        // mapeamentos cobrem o caso de o foco estar em outro componente
        mapearAtalho("copiar",   KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK, this::acaoCopiar);
        mapearAtalho("colar",    KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK, this::acaoColar);
        mapearAtalho("recortar", KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK, this::acaoRecortar);
        mapearAtalho("compilar", KeyEvent.VK_F7, 0, this::acaoCompilar);
        mapearAtalho("equipe",   KeyEvent.VK_F1, 0, this::acaoEquipe);
    }

    private void mapearAtalho(String nome, int key, int modifiers, Runnable acao) {
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Atalho exigido pelo trabalho (Ctrl), válido em todos os sistemas
        inputMap.put(KeyStroke.getKeyStroke(key, modifiers), nome);

        // No macOS a tecla de atalho nativa é Command; registrar as duas faz o
        // programa se comportar como esperado em qualquer sistema, sem que o
        // Ctrl deixe de funcionar
        if (modifiers == InputEvent.CTRL_DOWN_MASK) {
            int mascaraNativa = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
            if (mascaraNativa != InputEvent.CTRL_DOWN_MASK) {
                inputMap.put(KeyStroke.getKeyStroke(key, mascaraNativa), nome);
            }
        }

        getRootPane().getActionMap().put(nome, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                acao.run();
            }
        });
    }

    // ------------------------------------------------------------------
    // Ações dos botões
    // ------------------------------------------------------------------

    /**
     * 10. Novo: limpa editor, área de mensagens e barra de status, e volta ao
     * estado "arquivo novo" (esquece a pasta/nome do arquivo).
     */
    private void acaoNovo() {
        editorTextArea.setText("");
        messageTextArea.setText("");
        definirStatus(null);
        currentFile = null;
        editorTextArea.requestFocusInWindow();
    }

    /**
     * 11. Abrir: escolhe um .txt, carrega no editor, limpa as mensagens e
     * atualiza a barra de status. Se o usuário cancelar, nada é alterado.
     */
    private void acaoAbrir() {
        JFileChooser chooser = criarFileChooser();

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            // Cancelado: editor, mensagens e barra de status permanecem intactos
            return;
        }

        File selecionado = chooser.getSelectedFile();
        try {
            String conteudo = lerArquivo(selecionado);
            editorTextArea.setText(conteudo);
            editorTextArea.setCaretPosition(0);
            messageTextArea.setText("");
            currentFile = selecionado;
            // 8. Barra de status mostra a pasta e o nome do arquivo
            definirStatus(currentFile);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Erro ao abrir o arquivo: " + e.getMessage(),
                    "Abrir", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 12. Salvar: se o arquivo é novo, pergunta pasta/nome e atualiza a barra de
     * status; se já existe, grava no mesmo caminho e mantém a barra de status.
     * Em ambos os casos o texto permanece no editor e as mensagens são limpas.
     */
    private void acaoSalvar() {
        if (currentFile == null) {
            // (1) Arquivo novo: escolher pasta e nome
            JFileChooser chooser = criarFileChooser();
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File escolhido = garantirExtensaoTxt(chooser.getSelectedFile());
            if (!gravarArquivo(escolhido)) {
                // Falha na gravação: o estado anterior é preservado
                return;
            }
            currentFile = escolhido;
            definirStatus(currentFile);
            messageTextArea.setText("");
        } else {
            // (2) Arquivo já existente: grava no mesmo caminho
            if (!gravarArquivo(currentFile)) {
                return;
            }
            // A barra de status permanece como está
            messageTextArea.setText("");
        }
    }

    /** 13. Copiar: comportamento padrão de um editor de texto. */
    private void acaoCopiar() {
        editorTextArea.copy();
    }

    /** 13. Colar: comportamento padrão de um editor de texto. */
    private void acaoColar() {
        editorTextArea.paste();
    }

    /** 13. Recortar: comportamento padrão de um editor de texto. */
    private void acaoRecortar() {
        editorTextArea.cut();
    }

    /**
     * 14. Compilar (Parte 4): executa as análises léxica, sintática e semântica
     * sobre o conteúdo do editor. Se o programa estiver correto, grava o código
     * objeto (IL) em um arquivo .il, com o mesmo nome do programa fonte e na
     * MESMA pasta dele (teste01.txt gera teste01.il).
     */
    private void acaoCompilar() {
        String texto = editorTextArea.getText();
        messageTextArea.setText("");

        // O enunciado só prevê a compilação de arquivos já salvos: é o caminho
        // do arquivo que define onde (e com que nome) o .il será gerado.
        if (currentFile == null) {
            messageTextArea.setText("salve o programa antes de compilar");
            return;
        }

        Lexico lexico = new Lexico();
        Sintatico sintatico = new Sintatico();
        Semantico semantico = new Semantico();

        lexico.setInput(texto);

        try {
            sintatico.parse(lexico, semantico);
            gravarCodigoObjeto(semantico.getCodigoObjeto());
            messageTextArea.setText("programa compilado com sucesso");
        } catch (LexicalError e) {
            int linha = linhaDaPosicao(texto, e.getPosition());
            messageTextArea.setText("linha " + linha + ": erro léxico");
        } catch (SyntaticError e) {
            int posicao = e.getPosition();
            int linha = linhaDaPosicao(texto, posicao);

            if (ehErroLexicoPr(texto, posicao)) {
                messageTextArea.setText("linha " + linha + ": erro léxico");
                return;
            }

            String simboloEncontrado = obterSimboloEncontrado(texto, posicao);
            messageTextArea.setText("linha " + linha + ": encontrado " + simboloEncontrado + ", erro sintático");
        } catch (SemanticError e) {
            // A mensagem do erro semântico já descreve o problema; aqui só se
            // acrescenta a linha do token em que a ação semântica foi executada.
            int linha = linhaDaPosicao(texto, e.getPosition());
            messageTextArea.setText("linha " + linha + ": " + e.getMessage());
        } catch (IOException e) {
            messageTextArea.setText("erro ao gravar o código objeto: " + e.getMessage());
        }
    }

    /**
     * Grava o código objeto na mesma pasta do programa fonte, com o mesmo nome
     * e extensão .il. O arquivo usa as mesmas convenções dos programas salvos
     * (UTF-8, CRLF).
     */
    private void gravarCodigoObjeto(String codigo) throws IOException {
        String nome = currentFile.getName();
        int ponto = nome.lastIndexOf('.');
        String nomeBase = ponto > 0 ? nome.substring(0, ponto) : nome;
        File pasta = currentFile.getAbsoluteFile().getParentFile();
        File destino = new File(pasta, nomeBase + ".il");
        Files.write(destino.toPath(), codificar(codigo.replace("\n", QUEBRA_DE_LINHA)));
    }

    /**
     * Verifica se o token na posição do erro pertence ao token base t_pr
     * (palavra reservada inválida ou tentativa de identificador inválido),
     * que deve ser reportado como erro léxico.
     */
    private boolean ehErroLexicoPr(String texto, int posicao) {
        if (posicao < 0 || posicao >= texto.length()) {
            return false;
        }
        Lexico lexicoAux = new Lexico();
        lexicoAux.setInput(texto);
        lexicoAux.setPosition(posicao);
        try {
            Token t = lexicoAux.nextToken();
            return t != null && t.getId() == Constants.t_pr;
        } catch (LexicalError ex) {
            return true;
        }
    }

    /**
     * Retorna a representação exigida pelo enunciado para o símbolo encontrado
     * no erro sintático: EOF, constante_string ou o próprio lexema do token.
     */
    private String obterSimboloEncontrado(String texto, int posicao) {
        if (posicao < 0 || posicao >= texto.length()) {
            return "EOF";
        }
        Lexico lexicoAux = new Lexico();
        lexicoAux.setInput(texto);
        lexicoAux.setPosition(posicao);
        try {
            Token t = lexicoAux.nextToken();
            if (t == null) {
                return "EOF";
            }
            if (t.getId() == Constants.t_constante_string) {
                return "constante_string";
            }
            return t.getLexeme();
        } catch (LexicalError ex) {
            return "EOF";
        }
    }

    /**
     * Converte a posição absoluta no texto na linha correspondente (1-based).
     */
    private int linhaDaPosicao(String texto, int posicao) {
        int linha = 1;
        int limite = Math.min(Math.max(posicao, 0), texto.length());
        for (int i = 0; i < limite; i++) {
            if (texto.charAt(i) == '\n') {
                linha++;
            }
        }
        return linha;
    }

    /** 15. Equipe: exibe apenas os nomes dos integrantes. */
    private void acaoEquipe() {
        messageTextArea.setText("Isac Marlei Vicente Custódio\nFelipe Soares\nLucas Gonçalves Vestewig");
    }

    // ------------------------------------------------------------------
    // Leitura e gravação de arquivos (texto simples, compatível com o Notepad)
    // ------------------------------------------------------------------

    /**
     * Charset usado para reler arquivos ANSI (gerados pelo Notepad em Windows).
     * Windows-1252 não é exigido pela especificação da linguagem Java, então há
     * queda para ISO-8859-1, que é obrigatório em toda JVM e cobre os mesmos
     * caracteres acentuados nas posições relevantes.
     */
    private static final Charset CHARSET_ANSI =
            Charset.isSupported("windows-1252")
                    ? Charset.forName("windows-1252")
                    : StandardCharsets.ISO_8859_1;

    /** JFileChooser configurado para aceitar somente arquivos .txt. */
    private JFileChooser criarFileChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Arquivos de Texto (*.txt)", "txt"));
        chooser.setAcceptAllFileFilterUsed(false);
        return chooser;
    }

    /** Acrescenta a extensão .txt quando o usuário não a digita. */
    private File garantirExtensaoTxt(File arquivo) {
        if (arquivo.getName().toLowerCase().endsWith(".txt")) {
            return arquivo;
        }
        return new File(arquivo.getAbsolutePath() + ".txt");
    }

    /**
     * Lê um .txt gerado pelo Notepad. Aceita tanto arquivos UTF-8 (com ou sem
     * BOM) quanto arquivos ANSI/Windows-1252, e normaliza as quebras de linha
     * (CRLF ou CR) para o "\n" usado internamente pelo JTextArea.
     */
    private String lerArquivo(File arquivo) throws IOException {
        byte[] bytes = Files.readAllBytes(arquivo.toPath());
        String texto = decodificar(bytes);

        // O Notepad grava um BOM ao salvar em UTF-8
        if (!texto.isEmpty() && texto.charAt(0) == '﻿') {
            texto = texto.substring(1);
        }
        return texto.replace("\r\n", "\n").replace("\r", "\n");
    }

    /**
     * Tenta decodificar como UTF-8; se os bytes não formarem UTF-8 válido, o
     * arquivo é ANSI e é relido no charset de 1 byte (nunca falha).
     */
    private String decodificar(byte[] bytes) {
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, CHARSET_ANSI);
        }
    }

    /**
     * Converte o texto para bytes UTF-8, acrescentando o BOM apenas quando há
     * algum caractere fora do ASCII.
     *
     * O motivo: o Notepad de versões antigas do Windows lê um UTF-8 sem BOM
     * como ANSI e exibiria "variÃ¡vel" no lugar de "variável"; com o BOM ele
     * reconhece o UTF-8 corretamente. Quando o texto é ASCII puro (o caso normal
     * de um código-fonte), nenhum BOM é gravado e o arquivo fica byte a byte
     * igual em qualquer sistema.
     */
    private byte[] codificar(String texto) {
        byte[] conteudo = texto.getBytes(StandardCharsets.UTF_8);
        if (ehSomenteAscii(texto)) {
            return conteudo;
        }
        byte[] comBom = new byte[BOM_UTF8.length + conteudo.length];
        System.arraycopy(BOM_UTF8, 0, comBom, 0, BOM_UTF8.length);
        System.arraycopy(conteudo, 0, comBom, BOM_UTF8.length, conteudo.length);
        return comBom;
    }

    private boolean ehSomenteAscii(String texto) {
        for (int i = 0; i < texto.length(); i++) {
            if (texto.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }

    /**
     * Grava o conteúdo do editor em texto simples UTF-8, com quebras de linha
     * CRLF. A combinação é lida corretamente em Windows, Linux e macOS, e não
     * depende do sistema onde o arquivo foi gerado.
     *
     * @return true se a gravação foi concluída; false se houve erro.
     */
    private boolean gravarArquivo(File arquivo) {
        String texto = editorTextArea.getText().replace("\n", QUEBRA_DE_LINHA);
        try {
            Files.write(arquivo.toPath(), codificar(texto));
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Erro ao salvar o arquivo: " + e.getMessage(),
                    "Salvar", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Ícones desenhados por código (sem dependência de arquivos externos)
    // ------------------------------------------------------------------

    /**
     * Ícones 32x32 desenhados via Graphics2D, um para cada ação da barra de
     * ferramentas. Gerar por código evita ícones quebrados por falta de arquivo.
     */
    private static class AcaoIcon implements Icon {

        static final int NOVO = 0, ABRIR = 1, SALVAR = 2, COPIAR = 3;
        static final int COLAR = 4, RECORTAR = 5, COMPILAR = 6, EQUIPE = 7;

        private static final int TAMANHO = 32;

        private final int tipo;

        AcaoIcon(int tipo) {
            this.tipo = tipo;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.setStroke(new BasicStroke(1.5f));

            switch (tipo) {
                case NOVO:     desenharFolha(g2);    break;
                case ABRIR:    desenharPasta(g2);    break;
                case SALVAR:   desenharDisquete(g2); break;
                case COPIAR:   desenharCopia(g2);    break;
                case COLAR:    desenharPrancheta(g2);break;
                case RECORTAR: desenharTesoura(g2);  break;
                case COMPILAR: desenharPlay(g2);     break;
                case EQUIPE:   desenharEquipe(g2);   break;
                default:       break;
            }
            g2.dispose();
        }

        private void desenharFolha(Graphics2D g2) {
            g2.setColor(Color.WHITE);
            g2.fillRect(7, 3, 18, 26);
            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(7, 3, 18, 26);
            for (int i = 0; i < 4; i++) {
                g2.drawLine(11, 11 + i * 5, 21, 11 + i * 5);
            }
        }

        private void desenharPasta(Graphics2D g2) {
            g2.setColor(new Color(240, 190, 60));
            g2.fillRect(4, 9, 24, 17);
            g2.fillRect(4, 6, 11, 4);
            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(4, 9, 24, 17);
        }

        private void desenharDisquete(Graphics2D g2) {
            g2.setColor(new Color(60, 110, 200));
            g2.fillRect(4, 5, 24, 22);
            g2.setColor(Color.WHITE);
            g2.fillRect(10, 5, 12, 8);   // obturador
            g2.fillRect(9, 17, 14, 10);  // etiqueta
            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(4, 5, 24, 22);
        }

        private void desenharCopia(Graphics2D g2) {
            g2.setColor(Color.WHITE);
            g2.fillRect(5, 4, 16, 20);
            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(5, 4, 16, 20);
            g2.setColor(new Color(215, 215, 215));
            g2.fillRect(12, 9, 16, 20);
            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(12, 9, 16, 20);
        }

        private void desenharPrancheta(Graphics2D g2) {
            g2.setColor(new Color(220, 150, 60));
            g2.fillRect(5, 5, 22, 24);
            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(5, 5, 22, 24);
            g2.setColor(Color.WHITE);
            g2.fillRect(9, 10, 14, 15);
            g2.setColor(Color.GRAY);
            g2.fillRect(11, 3, 10, 5);   // presilha
        }

        private void desenharTesoura(Graphics2D g2) {
            g2.setColor(new Color(190, 60, 60));
            g2.drawLine(8, 4, 22, 22);
            g2.drawLine(24, 4, 10, 22);
            g2.drawOval(6, 22, 7, 7);
            g2.drawOval(19, 22, 7, 7);
        }

        private void desenharPlay(Graphics2D g2) {
            g2.setColor(new Color(50, 160, 70));
            g2.fillPolygon(new int[]{9, 9, 26}, new int[]{5, 27, 16}, 3);
            g2.setColor(Color.DARK_GRAY);
            g2.drawPolygon(new int[]{9, 9, 26}, new int[]{5, 27, 16}, 3);
        }

        private void desenharEquipe(Graphics2D g2) {
            g2.setColor(new Color(120, 90, 180));
            g2.fillOval(5, 6, 9, 9);      // cabeça 1
            g2.fillOval(3, 17, 13, 11);   // corpo 1
            g2.setColor(new Color(80, 140, 190));
            g2.fillOval(18, 6, 9, 9);     // cabeça 2
            g2.fillOval(16, 17, 13, 11);  // corpo 2
        }

        @Override
        public int getIconWidth() {
            return TAMANHO;
        }

        @Override
        public int getIconHeight() {
            return TAMANHO;
        }
    }

    // ------------------------------------------------------------------

    public static void main(String[] args) {
        // Visual nativo do sistema operacional (Windows 8 ou superior)
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Mantém o visual padrão do Swing caso não seja possível aplicar
        }

        SwingUtilities.invokeLater(() -> new CompiladorGUI().setVisible(true));
    }
}
