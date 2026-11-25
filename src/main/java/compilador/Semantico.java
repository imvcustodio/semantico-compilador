package compilador;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

/**
 * Analisador semântico e gerador de código intermediário (MSIL / IL).
 *
 * O analisador sintático (Sintatico.java, gerado pelo GALS) chama
 * executeAction(numero, token) sempre que, ao desempilhar, encontra uma ação
 * "#N" da gramática (BNF_t03-01.gals). O parâmetro "token" é o ÚLTIMO token já
 * consumido pelo analisador (previousToken), ou seja, o token que veio
 * imediatamente antes da ação na gramática.
 *
 * Convenção central do esquema de tradução: valores int64 da linguagem fonte
 * são mantidos como float64 na pilha da IL. Por isso:
 *   - ao CARREGAR (constante ou identificador) um int64 gera-se conv.r8;
 *   - ao ESCREVER ou ARMAZENAR em int64 gera-se conv.i8.
 */
public class Semantico implements Constants {

    // ------------------------------------------------------------------
    // Registros semânticos (descritos no esquema de tradução)
    // ------------------------------------------------------------------

    /** Operador relacional guardado pela ação #121 para uso na #122. */
    private String operadorRelacional = "";

    /** Código objeto (IL) gerado. */
    private final StringBuilder codigoObjeto = new StringBuilder();

    /** Pilha de tipos das expressões ("int64", "float64", "string", "bool"). */
    private final Stack<String> pilhaTipos = new Stack<>();

    /** Pilha de rótulos usada em seleção (if) e repetição (while). */
    private final Stack<String> pilhaRotulos = new Stack<>();

    /** Identificador guardado pela ação #103 e usado em #104..#108. */
    private String identificador = "";

    /**
     * Tabela de símbolos: identificador -> tamanho (0 = variável simples;
     * maior que 0 = vetor com esse tamanho). LinkedHashMap preserva a ordem de
     * declaração, o que dá uma mensagem de erro determinística.
     */
    private final Map<String, Integer> tabelaSimbolos = new LinkedHashMap<>();

    // ------------------------------------------------------------------
    // Registros auxiliares (não constam no esquema, mas são necessários)
    // ------------------------------------------------------------------

    /**
     * Identificadores de vetor cujo valor está sendo carregado em uma
     * expressão: a #127 empilha, a #133 desempilha. É uma pilha (e não uma
     * variável) porque os índices podem ser vetores: a[ b[ i_x ] ].
     */
    private final Stack<String> pilhaVetores = new Stack<>();

    /** Identificadores declarados que já foram usados (verificação 01). */
    private final Set<String> identificadoresUsados = new HashSet<>();

    /** Declarações da diretiva .locals, na ordem em que aparecem. */
    private final List<String> locais = new ArrayList<>();

    /** Posição em codigoObjeto onde a diretiva .locals deve ser inserida. */
    private int posicaoLocais = 0;

    /** Contador para gerar rótulos únicos (rotulo1, rotulo2, ...). */
    private int contadorRotulos = 0;

    // ------------------------------------------------------------------
    // Despacho das ações
    // ------------------------------------------------------------------

    public void executeAction(int action, Token token) throws SemanticError {
        switch (action) {
            case 101: acao101(); break;
            case 102: acao102(token); break;
            case 103: acao103(token); break;
            case 104: acao104(); break;
            case 105: acao105(token); break;
            case 106: acao106(); break;
            case 107: acao107(); break;
            case 108: acao108(); break;
            case 109: acao109(); break;
            case 110: acao110(token); break;
            case 111: acao111(); break;
            case 112: acao112(); break;
            case 113: acao113(); break;
            case 114: acao114(token); break;
            case 115: acao115(); break;
            case 116: acao116(); break;
            case 117: acao117(); break;
            case 118: acao118(); break;
            case 119: acao119(); break;
            case 120: acao120(token); break;
            case 121: acao121(token); break;
            case 122: acao122(); break;
            case 123: acao123(); break;
            case 124: acao124(); break;
            case 125: acao125(); break;
            case 126: acao126(); break;
            case 127: acao127(token); break;
            case 128: acao128(token); break;
            case 129: acao129(token); break;
            case 130: acao130(token); break;
            case 131: acao131(); break;
            case 132: acao132(); break;
            case 133: acao133(); break;
            case 134: acao134(); break;
            default:
                throw new SemanticError("Acao semantica nao implementada: " + action,
                        token == null ? -1 : token.getPosition());
        }
    }

    public String getCodigoObjeto() {
        return codigoObjeto.toString();
    }

    // ------------------------------------------------------------------
    // Programa
    // ------------------------------------------------------------------

    /** #101 (após "module"): cabeçalho do programa objeto. */
    private void acao101() {
        codigoObjeto.append(".assembly extern mscorlib {}\n");
        codigoObjeto.append(".assembly _codigo_objeto{}\n");
        codigoObjeto.append(".module _codigo_objeto.exe\n");
        codigoObjeto.append("\n");
        codigoObjeto.append(".class public _codigo_objeto{\n");
        codigoObjeto.append(".method static public void _principal(){\n");
        codigoObjeto.append(".entrypoint\n");
        // O .locals só pode ser escrito depois de conhecidas TODAS as
        // variáveis; por isso guardamos o ponto de inserção e o preenchemos
        // na #102.
        posicaoLocais = codigoObjeto.length();
    }

    /**
     * #102 (após o "}" final): verificações semânticas e término do programa.
     * O token recebido é o "}" final; é a linha dele que aparece na mensagem.
     */
    private void acao102(Token token) throws SemanticError {
        verificacao01(token);
        verificacao02(token);

        if (!locais.isEmpty()) {
            codigoObjeto.insert(posicaoLocais, ".locals(" + String.join(", ", locais) + ")\n");
        }
        codigoObjeto.append("ret\n");
        codigoObjeto.append("}\n");
        codigoObjeto.append("}\n");
    }

    // ------------------------------------------------------------------
    // Verificações semânticas
    // ------------------------------------------------------------------

    /**
     * Verificação 01 (comum a todas as equipes): todo identificador declarado
     * deve ser usado pelo menos no lado esquerdo de uma atribuição ou em um
     * comando de entrada.
     */
    private void verificacao01(Token token) throws SemanticError {
        for (String id : tabelaSimbolos.keySet()) {
            if (!identificadoresUsados.contains(id)) {
                throw new SemanticError(id + " identificador declarado, não utilizado",
                        token.getPosition());
            }
        }
    }

    /**
     * Verificação 02 (específica da equipe): compatibilidade de tipos do
     * operador lógico not. É feita na própria ação #120, porque é ali que o
     * tipo do operando está na pilhaTipos.
     */
    private void verificacao02(Token token) throws SemanticError {
        // ver acao120
    }

    // ------------------------------------------------------------------
    // Declaração de variáveis
    // ------------------------------------------------------------------

    /** #103 (após um identificador): guarda o identificador reconhecido. */
    private void acao103(Token token) {
        identificador = token.getLexeme();
    }

    /** #104: variável simples; tamanho 0 na tabela e uma entrada em .locals. */
    private void acao104() {
        tabelaSimbolos.put(identificador, 0);
        locais.add(tipoDe(identificador) + " " + identificador);
    }

    /**
     * #105 (após o cte_int de "[ cte_int ]"): vetor. O token é a constante
     * que dá o tamanho. Além do .locals, é preciso criar o vetor
     * (newarr) com esse tamanho, senão a variável seria null.
     */
    private void acao105(Token token) {
        String tamanho = token.getLexeme();
        int n;
        try {
            n = Integer.parseInt(tamanho);
        } catch (NumberFormatException e) {
            n = Integer.MAX_VALUE;
        }
        tabelaSimbolos.put(identificador, n);
        locais.add(tipoDe(identificador) + "[] " + identificador);

        codigoObjeto.append("ldc.i4 ").append(tamanho).append("\n");
        codigoObjeto.append("newarr ").append(classeDe(tipoDe(identificador))).append("\n");
        codigoObjeto.append("stloc ").append(identificador).append("\n");
    }

    // ------------------------------------------------------------------
    // Comandos de atribuição e de entrada
    // ------------------------------------------------------------------

    /**
     * #106 (fim da atribuição): o valor da expressão está no topo da pilha da
     * IL. Para vetor, a pilha da IL tem ainda (abaixo do valor) a referência
     * do vetor e o índice, empilhados em #107 e #134.
     */
    private void acao106() {
        pilhaTipos.pop(); // tipo da expressão (compatibilidade é assumida)
        String tipoId = tipoDe(identificador);
        marcarUsado(identificador);

        // int64 é mantido como float64 na pilha; ao gravar em variável int64
        // precisa voltar para int64. (A decisão é pelo tipo da VARIÁVEL: uma
        // expressão int64 gravada em variável float64 não deve ser convertida.)
        if ("int64".equals(tipoId)) {
            codigoObjeto.append("conv.i8\n");
        }
        if (ehVetor(identificador)) {
            codigoObjeto.append(instrucaoStelem(tipoId)).append("\n");
        } else {
            codigoObjeto.append("stloc ").append(identificador).append("\n");
        }
    }

    /** #107 (antes de "["): carrega a referência do vetor (primeiro operando de ldelem/stelem). */
    private void acao107() {
        codigoObjeto.append("ldloc ").append(identificador).append("\n");
    }

    /** #134 (após "]" de um vetor do lado esquerdo/entrada): o índice vira int32. */
    private void acao134() {
        pilhaTipos.pop(); // tipo da expressão do índice
        codigoObjeto.append("conv.i4\n");
    }

    /** #108 (comando de entrada): lê da entrada padrão e armazena no identificador. */
    private void acao108() {
        String tipoId = tipoDe(identificador);
        marcarUsado(identificador);

        codigoObjeto.append("call string [mscorlib]System.Console::ReadLine()\n");
        switch (tipoId) {
            case "int64":
                codigoObjeto.append("call int64 [mscorlib]System.Int64::Parse(string)\n");
                break;
            case "float64":
                codigoObjeto.append("call float64 [mscorlib]System.Double::Parse(string)\n");
                break;
            case "bool":
                codigoObjeto.append("call bool [mscorlib]System.Boolean::Parse(string)\n");
                break;
            default: // string: ReadLine já devolve o valor final
                break;
        }
        if (ehVetor(identificador)) {
            codigoObjeto.append(instrucaoStelem(tipoId)).append("\n");
        } else {
            codigoObjeto.append("stloc ").append(identificador).append("\n");
        }
    }

    // ------------------------------------------------------------------
    // Comando de saída
    // ------------------------------------------------------------------

    /** #109: escreve o valor do topo, conforme o tipo desempilhado. */
    private void acao109() {
        String tipo = pilhaTipos.pop();
        if ("int64".equals(tipo)) {
            codigoObjeto.append("conv.i8\n");
        }
        codigoObjeto.append("call void [mscorlib]System.Console::Write(" + tipo + ")\n");
    }

    // ------------------------------------------------------------------
    // Seleção (if) e repetição (while)
    // ------------------------------------------------------------------

    /**
     * #110 (logo após isTrueDo / isFalseDo): desvia para o rótulo de saída do
     * bloco quando a condição NÃO habilita o bloco. O token diz qual palavra
     * veio antes: isTrueDo -> desvia se falso; isFalseDo -> desvia se verdadeiro.
     */
    private void acao110(Token token) {
        pilhaTipos.pop();
        String rotulo = novoRotulo();
        codigoObjeto.append(desvioCondicional(token)).append(" ").append(rotulo).append("\n");
        pilhaRotulos.push(rotulo);
    }

    /** #111 (fim do if): coloca o rótulo que ficou no topo (fim do comando). */
    private void acao111() {
        codigoObjeto.append(pilhaRotulos.pop()).append(":\n");
    }

    /**
     * #112 (início da cláusula else): ao terminar o primeiro bloco, pula o
     * bloco do else; depois coloca o rótulo do else (rótulo desempilhado) e
     * deixa na pilha o rótulo de fim para a #111.
     */
    private void acao112() {
        String rotuloElse = pilhaRotulos.pop();
        String rotuloFim = novoRotulo();
        codigoObjeto.append("br ").append(rotuloFim).append("\n");
        codigoObjeto.append(rotuloElse).append(":\n");
        pilhaRotulos.push(rotuloFim);
    }

    /** #113 (antes de while): rótulo de início, para repetir a avaliação da condição. */
    private void acao113() {
        String rotuloInicio = novoRotulo();
        codigoObjeto.append(rotuloInicio).append(":\n");
        pilhaRotulos.push(rotuloInicio);
    }

    /** #114 (após isTrueDo/isFalseDo do while): desvio condicional para fora do laço. */
    private void acao114(Token token) {
        pilhaTipos.pop();
        String rotuloFim = novoRotulo();
        codigoObjeto.append(desvioCondicional(token)).append(" ").append(rotuloFim).append("\n");
        pilhaRotulos.push(rotuloFim);
    }

    /** #115 (fim do while): volta ao início e coloca o rótulo de saída. */
    private void acao115() {
        String rotuloFim = pilhaRotulos.pop();
        String rotuloInicio = pilhaRotulos.pop();
        codigoObjeto.append("br ").append(rotuloInicio).append("\n");
        codigoObjeto.append(rotuloFim).append(":\n");
    }

    // ------------------------------------------------------------------
    // Expressões lógicas e relacionais
    // ------------------------------------------------------------------

    /** #116 (and). */
    private void acao116() {
        pilhaTipos.pop();
        pilhaTipos.pop();
        pilhaTipos.push("bool");
        codigoObjeto.append("and\n");
    }

    /** #117 (or). */
    private void acao117() {
        pilhaTipos.pop();
        pilhaTipos.pop();
        pilhaTipos.push("bool");
        codigoObjeto.append("or\n");
    }

    /** #118 (true). */
    private void acao118() {
        pilhaTipos.push("bool");
        codigoObjeto.append("ldc.i4.1\n");
    }

    /** #119 (false). */
    private void acao119() {
        pilhaTipos.push("bool");
        codigoObjeto.append("ldc.i4.0\n");
    }

    /**
     * #120 (not): verificação semântica 02 (específica da equipe). O operando
     * de "not" precisa ser bool; caso contrário, encerra a análise com o erro
     * "tipo incompatível em operador lógico not". O token é o último do
     * operando, então a linha apontada é a da expressão negada.
     * Para bool (0 ou 1), not x = x xor 1.
     */
    private void acao120(Token token) throws SemanticError {
        String tipo = pilhaTipos.pop();
        if (!"bool".equals(tipo)) {
            throw new SemanticError("tipo incompatível em operador lógico not",
                    token.getPosition());
        }
        pilhaTipos.push("bool");
        codigoObjeto.append("ldc.i4.1\n");
        codigoObjeto.append("xor\n");
    }

    /** #121 (após o operador relacional): guarda o operador para a #122. */
    private void acao121(Token token) {
        operadorRelacional = token.getLexeme();
    }

    /** #122 (após o 2º operando): gera a comparação; resultado é bool. */
    private void acao122() {
        String tipo2 = pilhaTipos.pop();
        String tipo1 = pilhaTipos.pop();
        pilhaTipos.push("bool");

        if ("string".equals(tipo1) && "string".equals(tipo2)) {
            // ceq/clt/cgt comparariam referências; strings precisam de chamadas.
            switch (operadorRelacional) {
                case "=":
                    codigoObjeto.append("call bool [mscorlib]System.String::op_Equality(string, string)\n");
                    break;
                case "<>":
                    codigoObjeto.append("call bool [mscorlib]System.String::op_Equality(string, string)\n");
                    codigoObjeto.append("ldc.i4.0\n");
                    codigoObjeto.append("ceq\n");
                    break;
                case "<":
                    codigoObjeto.append("call int32 [mscorlib]System.String::CompareOrdinal(string, string)\n");
                    codigoObjeto.append("ldc.i4.0\n");
                    codigoObjeto.append("clt\n");
                    break;
                default: // ">"
                    codigoObjeto.append("call int32 [mscorlib]System.String::CompareOrdinal(string, string)\n");
                    codigoObjeto.append("ldc.i4.0\n");
                    codigoObjeto.append("cgt\n");
                    break;
            }
        } else {
            switch (operadorRelacional) {
                case "=":
                    codigoObjeto.append("ceq\n");
                    break;
                case "<>":
                    codigoObjeto.append("ceq\n");
                    codigoObjeto.append("ldc.i4.0\n");
                    codigoObjeto.append("ceq\n");
                    break;
                case "<":
                    codigoObjeto.append("clt\n");
                    break;
                default: // ">"
                    codigoObjeto.append("cgt\n");
                    break;
            }
        }
    }

    // ------------------------------------------------------------------
    // Expressões aritméticas
    // ------------------------------------------------------------------

    /** #123 (+). */
    private void acao123() {
        empilhaTipoAritmetico();
        codigoObjeto.append("add\n");
    }

    /** #124 (-). */
    private void acao124() {
        empilhaTipoAritmetico();
        codigoObjeto.append("sub\n");
    }

    /** #125 (*). */
    private void acao125() {
        empilhaTipoAritmetico();
        codigoObjeto.append("mul\n");
    }

    /** #126 (/): o resultado é sempre float64. */
    private void acao126() {
        pilhaTipos.pop();
        pilhaTipos.pop();
        pilhaTipos.push("float64");
        codigoObjeto.append("div\n");
    }

    /** #127 (identificador em expressão): carrega o valor (ou a referência do vetor). */
    private void acao127(Token token) {
        String id = token.getLexeme();
        String tipo = tipoDe(id);
        pilhaTipos.push(tipo);
        codigoObjeto.append("ldloc ").append(id).append("\n");
        if (ehVetor(id)) {
            // A #133 completa o carregamento do elemento, após o índice.
            pilhaVetores.push(id);
        } else if ("int64".equals(tipo)) {
            codigoObjeto.append("conv.r8\n");
        }
    }

    /** #128 (cte_int). */
    private void acao128(Token token) {
        pilhaTipos.push("int64");
        codigoObjeto.append("ldc.i8 ").append(token.getLexeme()).append("\n");
        codigoObjeto.append("conv.r8\n");
    }

    /** #129 (cte_float): a linguagem usa vírgula decimal; a IL exige ponto. */
    private void acao129(Token token) {
        pilhaTipos.push("float64");
        codigoObjeto.append("ldc.r8 ").append(token.getLexeme().replace(',', '.')).append("\n");
    }

    /** #130 (cte_string): o lexema já vem com as aspas, como a IL exige. */
    private void acao130(Token token) {
        pilhaTipos.push("string");
        codigoObjeto.append("ldstr ").append(token.getLexeme()).append("\n");
    }

    /** #131 (+ unário): não altera o valor nem o tipo (int64 permanece int64). */
    private void acao131() {
        String tipo = pilhaTipos.pop();
        pilhaTipos.push("int64".equals(tipo) ? "int64" : "float64");
    }

    /** #132 (- unário): multiplica por -1 (em float64, que é como a pilha guarda). */
    private void acao132() {
        String tipo = pilhaTipos.pop();
        pilhaTipos.push("int64".equals(tipo) ? "int64" : "float64");
        codigoObjeto.append("ldc.i8 -1\n");
        codigoObjeto.append("conv.r8\n");
        codigoObjeto.append("mul\n");
    }

    /** #133 (após "]" de vetor em expressão): carrega o elemento. */
    private void acao133() {
        pilhaTipos.pop(); // tipo do índice
        String id = pilhaVetores.pop();
        String tipo = tipoDe(id);
        codigoObjeto.append("conv.i4\n");
        codigoObjeto.append(instrucaoLdelem(tipo)).append("\n");
        if ("int64".equals(tipo)) {
            codigoObjeto.append("conv.r8\n");
        }
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    /** Desempilha dois tipos e empilha int64 (se ambos int64) ou float64. */
    private void empilhaTipoAritmetico() {
        String tipo1 = pilhaTipos.pop();
        String tipo2 = pilhaTipos.pop();
        if ("int64".equals(tipo1) && "int64".equals(tipo2)) {
            pilhaTipos.push("int64");
        } else {
            pilhaTipos.push("float64");
        }
    }

    /** Tipo definido pelo prefixo: i_ int64, f_ float64, s_ string, b_ bool. */
    private String tipoDe(String id) {
        switch (id.charAt(0)) {
            case 'i': return "int64";
            case 'f': return "float64";
            case 's': return "string";
            default:  return "bool";
        }
    }

    /** Nome da classe .NET correspondente, usado em newarr. */
    private String classeDe(String tipo) {
        switch (tipo) {
            case "int64":   return "[mscorlib]System.Int64";
            case "float64": return "[mscorlib]System.Double";
            case "string":  return "[mscorlib]System.String";
            default:        return "[mscorlib]System.Boolean";
        }
    }

    private String instrucaoLdelem(String tipo) {
        switch (tipo) {
            case "int64":   return "ldelem.i8";
            case "float64": return "ldelem.r8";
            case "string":  return "ldelem.ref";
            default:        return "ldelem.u1";
        }
    }

    private String instrucaoStelem(String tipo) {
        switch (tipo) {
            case "int64":   return "stelem.i8";
            case "float64": return "stelem.r8";
            case "string":  return "stelem.ref";
            default:        return "stelem.i1";
        }
    }

    /** brfalse para isTrueDo (executa o bloco se verdadeiro); brtrue para isFalseDo. */
    private String desvioCondicional(Token token) {
        return token.getId() == t_isTrueDo ? "brfalse" : "brtrue";
    }

    private boolean ehVetor(String id) {
        Integer tamanho = tabelaSimbolos.get(id);
        return tamanho != null && tamanho > 0;
    }

    private void marcarUsado(String id) {
        identificadoresUsados.add(id);
    }

    private String novoRotulo() {
        contadorRotulos++;
        return "rotulo" + contadorRotulos;
    }
}
