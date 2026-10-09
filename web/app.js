"use strict";

// ---------------------------------------------------------------------------------------------------------------
// Apoio: montar elementos, falar com a API, sessão
// ---------------------------------------------------------------------------------------------------------------

/**
 * Cria um elemento. Todo texto entra como nó de texto (nunca como HTML), então nada do que as pessoas digitam em
 * títulos, enunciados ou nomes consegue virar código na página.
 */
function h(tag, props, ...filhos) {
    const el = document.createElement(tag);
    for (const [chave, valor] of Object.entries(props || {})) {
        if (valor == null || valor === false) continue;
        if (chave.startsWith("on")) el.addEventListener(chave.slice(2), valor);
        else if (chave === "class") el.className = valor;
        else if (chave in el && chave !== "list") el[chave] = valor;
        else el.setAttribute(chave, valor === true ? "" : valor);
    }
    el.append(...filhos.flat(Infinity).filter(f => f != null && f !== false));
    return el;
}

const CHAVE_SESSAO = "quizlab.sessao";
const CHAVE_DESTINO = "quizlab.destino";
const CHAVE_PENDENTE = "quizlab.pendente";
const CHAVE_NOME = "quizlab.nome";
let sessao = lerSessao();

/**
 * O resultado de quem acabou de responder sem conta. Fica só na memória desta página, de propósito: recarregou ou
 * fechou, sumiu. O que vai para o armazenamento da aba é só o id e a chave da tentativa, para ela poder ser
 * guardada na conta se a pessoa criar uma logo em seguida.
 */
let resultadoSemConta = null;

function guardarNaAba(chave, valor) {
    try {
        if (valor == null) sessionStorage.removeItem(chave);
        else sessionStorage.setItem(chave, JSON.stringify(valor));
    } catch {
        // Sem armazenamento, só se perde a conveniência.
    }
}

function lerDaAba(chave) {
    try {
        return JSON.parse(sessionStorage.getItem(chave));
    } catch {
        return null;
    }
}

function lerSessao() {
    try {
        return JSON.parse(localStorage.getItem(CHAVE_SESSAO));
    } catch {
        return null;
    }
}

function guardarSessao(nova) {
    sessao = nova;
    try {
        if (nova) localStorage.setItem(CHAVE_SESSAO, JSON.stringify(nova));
        else localStorage.removeItem(CHAVE_SESSAO);
    } catch {
        // Navegador sem armazenamento (aba anônima restrita): a sessão vale só até recarregar.
    }
}

let pedidosEmAndamento = 0;
let relogioLento = null;

async function api(caminho, { metodo = "GET", corpo } = {}) {
    const cabecalhos = {};
    if (corpo !== undefined) cabecalhos["Content-Type"] = "application/json";
    if (sessao) cabecalhos["Authorization"] = "Bearer " + sessao.token;

    pedidosEmAndamento++;
    relogioLento ??= setTimeout(() => { document.getElementById("aviso-lento").hidden = false; }, 4000);
    let resposta;
    try {
        resposta = await fetch(window.API_URL + caminho, {
            method: metodo,
            headers: cabecalhos,
            body: corpo === undefined ? undefined : JSON.stringify(corpo),
        });
    } catch {
        throw new Error("Não foi possível falar com o servidor. Confira a sua conexão e tente de novo.");
    } finally {
        if (--pedidosEmAndamento === 0) {
            clearTimeout(relogioLento);
            relogioLento = null;
            document.getElementById("aviso-lento").hidden = true;
        }
    }

    if (resposta.status === 204) return null;
    const dados = await resposta.json().catch(() => null);
    if (resposta.status === 401 && sessao) {
        // O token venceu ou foi encerrado em outro lugar: volta para o login e retoma de onde estava.
        guardarSessao(null);
        if (caminhoAtual().startsWith("/q/")) {
            // Um quiz abre sem conta, então dá para continuar na mesma página.
            navegar();
        } else {
            lembrarDestino(caminhoAtual());
            location.hash = "#/entrar";
        }
        throw new Error("A sua sessão terminou. Entre de novo.");
    }
    if (!resposta.ok) throw new Error(dados?.erro || "Algo deu errado. Tente de novo.");
    return dados;
}

function caminhoAtual() {
    return location.hash.slice(1) || "/";
}

function lembrarDestino(caminho) {
    try {
        sessionStorage.setItem(CHAVE_DESTINO, caminho);
    } catch {
        // Sem armazenamento, a pessoa só cai na página inicial depois de entrar.
    }
}

function pegarDestino() {
    try {
        const destino = sessionStorage.getItem(CHAVE_DESTINO);
        sessionStorage.removeItem(CHAVE_DESTINO);
        return destino;
    } catch {
        return null;
    }
}

let relogioRecado = null;

function recado(texto) {
    const el = document.getElementById("recado");
    el.textContent = texto;
    el.hidden = false;
    clearTimeout(relogioRecado);
    relogioRecado = setTimeout(() => { el.hidden = true; }, 4000);
}

// ---------------------------------------------------------------------------------------------------------------
// Apoio: textos e formatos
// ---------------------------------------------------------------------------------------------------------------

const TIPOS = {
    UNICA: { nome: "Múltipla escolha", dica: "Marque a alternativa correta." },
    MULTIPLA: { nome: "Caixas de seleção", dica: "Marque todas as alternativas corretas." },
    VERDADEIRO_FALSO: { nome: "Verdadeiro ou falso", dica: "Julgue cada afirmação: V para verdadeira, F para falsa." },
};

const numero = valor => Number(valor).toLocaleString("pt-BR", { maximumFractionDigits: 2 });
const porcento = (pontos, total) => total ? Math.round((pontos / total) * 100) : 0;
const plural = (n, um, varios) => `${n} ${n === 1 ? um : varios}`;

function data(iso) {
    return new Date(iso).toLocaleString("pt-BR", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

/** Data compacta para tabelas: 09/10/26, 10:40. */
function dataCurta(iso) {
    return new Date(iso).toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", year: "2-digit", hour: "2-digit", minute: "2-digit" });
}

function linkDoQuiz(codigo) {
    return `${location.origin}${location.pathname}#/q/${codigo}`;
}

async function copiarLink(codigo) {
    const link = linkDoQuiz(codigo);
    try {
        await navigator.clipboard.writeText(link);
        recado("Link copiado. É só colar e enviar.");
    } catch {
        window.prompt("Copie o link do quiz:", link);
    }
}

/** Classe de cor para uma nota: boa, média ou baixa. */
function faixa(percentual) {
    return percentual >= 70 ? "boa" : percentual >= 40 ? "media" : "baixa";
}

function vazio(titulo, texto, acao) {
    return h("div", { class: "vazio" }, h("h2", null, titulo), h("p", null, texto), acao);
}

function cabecalho(titulo, subtitulo, acao) {
    return h("div", { class: "cabecalho" },
        h("div", null, h("h1", null, titulo), subtitulo && h("p", { class: "sub" }, subtitulo)),
        acao);
}

function seloTema(tema) {
    return tema && h("span", { class: "selo" }, tema);
}

// ---------------------------------------------------------------------------------------------------------------
// Entrar e criar conta
// ---------------------------------------------------------------------------------------------------------------

function telaConta(criando) {
    const veioDeUmQuiz = (lerDestino() || "").startsWith("/q/");
    const pendente = lerDaAba(CHAVE_PENDENTE);
    const erro = h("p", { class: "erro", role: "alert", hidden: true });
    const botao = h("button", { type: "submit", class: "botao primario largo" }, criando ? "Criar conta" : "Entrar");

    const campo = (rotulo, atributos, ajuda) => h("label", { class: "campo" },
        h("span", null, rotulo), h("input", { type: "text", required: true, ...atributos }), ajuda && h("small", null, ajuda));

    const form = h("form", { class: "cartao form-conta", novalidate: true, onsubmit: enviar },
        h("h1", null, criando ? "Criar conta" : "Entrar"),
        h("p", { class: "sub" }, pendente
            ? (criando ? "Crie a conta e o resultado do quiz que você acabou de responder vai para o seu histórico."
                : "Entre e o resultado do quiz que você acabou de responder vai para o seu histórico.")
            : veioDeUmQuiz
            ? "Entre para responder com a sua conta e guardar o resultado no histórico."
            : criando ? "Com uma conta você responde quizzes, cria os seus e acompanha os seus resultados."
                : "Entre para responder quizzes e ver os seus resultados."),
        criando && campo("Nome", { name: "nome", autocomplete: "name", maxLength: 80 }),
        campo("E-mail", { name: "email", type: "email", autocomplete: "email", maxLength: 160 }),
        campo("Senha", { name: "senha", type: "password", autocomplete: criando ? "new-password" : "current-password", maxLength: 72 },
            criando && "Pelo menos 8 caracteres. Ela é guardada criptografada."),
        erro,
        botao,
        h("p", { class: "troca" }, criando ? "Já tem conta? " : "Ainda não tem conta? ",
            h("a", { href: criando ? "#/entrar" : "#/criar-conta" }, criando ? "Entrar" : "Criar conta")));

    async function enviar(evento) {
        evento.preventDefault();
        const dados = Object.fromEntries(new FormData(form));
        dados.email = dados.email.trim();
        const problema = criando && !dados.nome.trim() ? "Informe o seu nome."
            : !dados.email ? "Informe o seu e-mail."
            : criando && dados.senha.length < 8 ? "A senha precisa de pelo menos 8 caracteres."
            : !dados.senha ? "Informe a sua senha." : null;
        if (problema) return mostrar(problema);

        botao.disabled = true;
        try {
            guardarSessao(await api(criando ? "/api/contas" : "/api/login", { metodo: "POST", corpo: dados }));
            let destino = pegarDestino() || "/";
            if (pendente) {
                guardarNaAba(CHAVE_PENDENTE, null);
                resultadoSemConta = null;
                try {
                    await api(`/api/tentativas/${pendente.id}/reivindicar`, { metodo: "POST", corpo: { chave: pendente.chave } });
                    destino = "/resultado/" + pendente.id;
                    recado("Resultado guardado no seu histórico.");
                } catch {
                    // A conta foi criada de qualquer jeito; só o resultado é que não pôde ser guardado.
                    recado("A conta está pronta, mas não foi possível guardar aquele resultado.");
                }
            }
            location.hash = "#" + destino;
        } catch (e) {
            mostrar(e.message);
        } finally {
            botao.disabled = false;
        }
    }

    function mostrar(mensagem) {
        erro.textContent = mensagem;
        erro.hidden = false;
    }

    return h("div", { class: "centro" }, form);
}

function lerDestino() {
    try {
        return sessionStorage.getItem(CHAVE_DESTINO);
    } catch {
        return null;
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Explorar (quizzes públicos)
// ---------------------------------------------------------------------------------------------------------------

async function telaExplorar() {
    const quizzes = await api("/api/quizzes");
    if (!quizzes.length) {
        return h("div", null,
            cabecalho("Explorar", "Quizzes públicos criados por quem usa o QuizLab."),
            vazio("Ainda não há quizzes públicos", "Que tal criar o primeiro?",
                h("a", { class: "botao primario", href: "#/novo" }, "Criar quiz")));
    }

    const grade = h("div", { class: "grade" });
    const nada = h("p", { class: "sub", hidden: true }, "Nenhum quiz combina com a busca.");
    const cartoes = quizzes.map(q => ({
        texto: [q.titulo, q.tema, q.autor, q.descricao].join(" ").toLowerCase(),
        el: h("a", { class: "cartao quiz", href: "#/q/" + q.codigo },
            seloTema(q.tema),
            h("h2", null, q.titulo),
            q.descricao && h("p", { class: "descricao" }, q.descricao),
            h("p", { class: "rodape" }, `${plural(q.questoes, "questão", "questões")} · por ${q.autor}`)),
    }));
    grade.append(...cartoes.map(c => c.el));

    const busca = h("input", {
        type: "search", class: "busca", placeholder: "Buscar por título, tema ou autor", "aria-label": "Buscar quizzes",
        oninput: () => {
            const termo = busca.value.trim().toLowerCase();
            let visiveis = 0;
            for (const c of cartoes) {
                c.el.hidden = !c.texto.includes(termo);
                if (!c.el.hidden) visiveis++;
            }
            nada.hidden = visiveis > 0;
        },
    });

    return h("div", null,
        cabecalho("Explorar", "Quizzes públicos criados por quem usa o QuizLab.", busca), grade, nada);
}

// ---------------------------------------------------------------------------------------------------------------
// Meus quizzes
// ---------------------------------------------------------------------------------------------------------------

async function telaMeus() {
    const quizzes = await api("/api/quizzes/meus");
    const criar = h("div", { class: "acoes" },
        h("button", { type: "button", class: "botao", onclick: importarQuiz }, "Importar arquivo"),
        h("a", { class: "botao primario", href: "#/novo" }, "Criar quiz"));
    if (!quizzes.length) {
        return h("div", null, cabecalho("Meus quizzes"),
            vazio("Você ainda não criou nenhum quiz",
                "Monte as questões aqui ou importe um arquivo pronto, depois copie o link e envie para quem quiser.", criar));
    }

    const lista = h("div", { class: "lista" }, quizzes.map(q => {
        const selo = h("span", { class: "selo " + (q.publico ? "publico" : "") });
        const alternar = h("button", { type: "button", class: "botao discreto", onclick: mudarVisibilidade });
        const pintar = () => {
            selo.textContent = q.publico ? "Público" : "Só por link";
            alternar.textContent = q.publico ? "Tirar do Explorar" : "Publicar no Explorar";
        };
        pintar();

        async function mudarVisibilidade() {
            alternar.disabled = true;
            try {
                await api(`/api/quizzes/${q.codigo}/publico`, { metodo: "PUT", corpo: { publico: !q.publico } });
                q.publico = !q.publico;
                pintar();
                recado(q.publico ? "Agora o quiz aparece em Explorar." : "Agora o quiz só abre pelo link.");
            } catch (e) {
                recado(e.message);
            } finally {
                alternar.disabled = false;
            }
        }

        const item = h("article", { class: "cartao item" },
            h("div", { class: "item-texto" },
                h("div", { class: "selos" }, selo, seloTema(q.tema)),
                h("h2", null, q.titulo),
                h("p", { class: "rodape" },
                    `${plural(q.questoes, "questão", "questões")} · ${plural(q.tentativas, "resposta", "respostas")} · criado em ${data(q.criadoEm)}`)),
            h("div", { class: "acoes" },
                h("button", { type: "button", class: "botao primario", onclick: () => copiarLink(q.codigo) }, "Copiar link"),
                h("a", { class: "botao", href: "#/respostas/" + q.codigo }, "Relatório"),
                h("a", { class: "botao", href: "#/q/" + q.codigo }, "Responder"),
                h("a", { class: "botao", href: "#/editar/" + q.codigo }, "Editar"),
                h("button", { type: "button", class: "botao", onclick: () => exportarQuiz(q.codigo) }, "Exportar"),
                alternar,
                h("button", { type: "button", class: "botao perigo", onclick: excluir }, "Excluir")));

        async function excluir() {
            const aviso = q.tentativas
                ? `Excluir "${q.titulo}" de vez? O quiz e a lista de respostas são apagados e não dá para desfazer. `
                    + "Quem já respondeu continua vendo só o próprio resultado."
                : `Excluir "${q.titulo}" de vez? Não dá para desfazer.`;
            if (!window.confirm(aviso)) return;
            try {
                await api("/api/quizzes/" + q.codigo, { metodo: "DELETE" });
                item.remove();
                recado("Quiz excluído.");
                if (!lista.children.length) navegar();
            } catch (e) {
                recado(e.message);
            }
        }

        return item;
    }));

    return h("div", null, cabecalho("Meus quizzes", "Copie o link de um quiz para compartilhar.", criar), lista);
}

// ---------------------------------------------------------------------------------------------------------------
// Importar e exportar quiz em arquivo
// ---------------------------------------------------------------------------------------------------------------

/** O arquivo é um JSON com os mesmos campos do editor. O formato está documentado no README. */
const FORMATO_DO_ARQUIVO = "quizlab-1";
const TAMANHO_MAXIMO_DO_ARQUIVO = 1024 * 1024;

const MODELO_DE_QUIZ = {
    formato: FORMATO_DO_ARQUIVO,
    titulo: "Título do quiz",
    tema: "Tema (opcional)",
    descricao: "Descrição (opcional)",
    publico: true,
    embaralharQuestoes: false,
    embaralharAlternativas: false,
    questoes: [
        {
            tipo: "UNICA",
            enunciado: "Múltipla escolha: exatamente uma alternativa com correta = true.",
            explicacao: "Opcional. Aparece no resultado, depois que a pessoa responde.",
            alternativas: [
                { texto: "Alternativa errada", correta: false },
                { texto: "Alternativa certa", correta: true },
                { texto: "Outra errada", correta: false },
            ],
        },
        {
            tipo: "MULTIPLA",
            enunciado: "Caixas de seleção: uma ou mais alternativas com correta = true.",
            alternativas: [
                { texto: "Certa", correta: true },
                { texto: "Também certa", correta: true },
                { texto: "Errada", correta: false },
            ],
        },
        {
            tipo: "VERDADEIRO_FALSO",
            enunciado: "Verdadeiro ou falso: cada alternativa é uma afirmação; correta = true quer dizer verdadeira.",
            alternativas: [
                { texto: "Uma afirmação verdadeira", correta: true },
                { texto: "Uma afirmação falsa", correta: false },
            ],
        },
    ],
};

/** Quiz lido de um arquivo, esperando o editor abrir para ser conferido e salvo. */
let quizImportado = null;

/** Recebe um objeto (vira JSON) ou um texto já pronto, como o CSV do relatório. */
function baixarArquivo(nome, conteudo, tipo = "application/json") {
    const texto = typeof conteudo === "string" ? conteudo : JSON.stringify(conteudo, null, 2);
    const url = URL.createObjectURL(new Blob([texto], { type: tipo }));
    const link = h("a", { href: url, download: nome, hidden: true });
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function nomeDeArquivo(titulo) {
    const base = titulo.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase()
        .replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 60);
    return (base || "quiz") + ".quiz.json";
}

async function exportarQuiz(codigo) {
    try {
        const quiz = await api(`/api/quizzes/${codigo}/edicao`);
        baixarArquivo(nomeDeArquivo(quiz.titulo), {
            formato: FORMATO_DO_ARQUIVO,
            titulo: quiz.titulo, tema: quiz.tema || "", descricao: quiz.descricao || "", publico: quiz.publico,
            embaralharQuestoes: quiz.embaralharQuestoes, embaralharAlternativas: quiz.embaralharAlternativas,
            questoes: quiz.questoes.map(q => ({
                tipo: q.tipo, enunciado: q.enunciado, explicacao: q.explicacao || "",
                alternativas: q.alternativas.map(a => ({ texto: a.texto, correta: a.correta })),
            })),
        });
        recado("Arquivo baixado. Ele contém o gabarito: não envie para quem vai responder.");
    } catch (e) {
        recado(e.message);
    }
}

/** Abre a janela de escolher arquivo e devolve o texto dele. Se a pessoa cancelar, nunca resolve. */
function escolherArquivo() {
    return new Promise((resolver, rejeitar) => {
        const campo = h("input", { type: "file", accept: ".json,application/json", hidden: true });
        campo.addEventListener("cancel", () => campo.remove());
        campo.addEventListener("change", async () => {
            const arquivo = campo.files[0];
            campo.remove();
            if (!arquivo) return;
            if (arquivo.size > TAMANHO_MAXIMO_DO_ARQUIVO) return rejeitar(new Error("O arquivo é grande demais (máximo de 1 MB)."));
            try {
                resolver(await arquivo.text());
            } catch {
                rejeitar(new Error("Não foi possível ler o arquivo."));
            }
        });
        document.body.append(campo);
        campo.click();
    });
}

/**
 * Transforma o texto do arquivo no quiz que o editor usa. Aqui só se confere a estrutura; o conteúdo (enunciado
 * vazio, nenhuma correta marcada…) é conferido ao salvar, com o quiz já aberto no editor para a pessoa corrigir.
 */
function lerQuizDeArquivo(conteudo) {
    let dados;
    try {
        dados = JSON.parse(conteudo);
    } catch {
        throw new Error("O arquivo não é um JSON válido. Baixe o modelo para ver o formato.");
    }
    // Aceita também só a lista de questões, sem título.
    if (Array.isArray(dados)) dados = { questoes: dados };
    if (!dados || typeof dados !== "object" || !Array.isArray(dados.questoes) || !dados.questoes.length) {
        throw new Error('O arquivo precisa ter uma lista "questoes" com pelo menos uma questão. Baixe o modelo para ver o formato.');
    }
    if (dados.questoes.length > 100) throw new Error("O arquivo tem mais de 100 questões, que é o máximo por quiz.");

    const texto = valor => valor == null ? "" : String(valor);
    const questoes = dados.questoes.map((q, i) => {
        const n = `Questão ${i + 1} do arquivo: `;
        if (!q || typeof q !== "object") throw new Error(n + "não é uma questão válida.");
        if (!Array.isArray(q.alternativas) || !q.alternativas.length) throw new Error(n + 'falta a lista "alternativas".');
        if (q.alternativas.length > 10) throw new Error(n + "tem mais de 10 alternativas, que é o máximo.");

        const alternativas = q.alternativas.map(a => typeof a === "string"
            ? { texto: a, correta: null }
            : { texto: texto(a?.texto), correta: typeof a?.correta === "boolean" ? a.correta : null });
        const tipo = tipoDoArquivo(q.tipo, alternativas);
        if (!tipo) throw new Error(n + `tipo "${texto(q.tipo)}" desconhecido. Use UNICA, MULTIPLA ou VERDADEIRO_FALSO.`);
        // Em questões de escolha, alternativa sem "correta" é uma alternativa errada. Em V/F fica sem gabarito,
        // e o editor cobra a escolha antes de salvar.
        if (tipo !== "VERDADEIRO_FALSO") alternativas.forEach(a => { a.correta = a.correta === true; });
        return { tipo, enunciado: texto(q.enunciado), explicacao: texto(q.explicacao), alternativas };
    });

    return {
        titulo: texto(dados.titulo), tema: texto(dados.tema), descricao: texto(dados.descricao),
        publico: dados.publico !== false,
        embaralharQuestoes: dados.embaralharQuestoes === true, embaralharAlternativas: dados.embaralharAlternativas === true,
        questoes,
    };
}

function tipoDoArquivo(tipo, alternativas) {
    if (tipo == null || tipo === "") {
        // Sem tipo: uma correta é múltipla escolha, mais de uma são caixas de seleção.
        return alternativas.filter(a => a.correta === true).length > 1 ? "MULTIPLA" : "UNICA";
    }
    const limpo = String(tipo).trim().toUpperCase().replace(/[\s-]+/g, "_");
    if (["VF", "V_F", "V/F", "VERDADEIRO_OU_FALSO"].includes(limpo)) return "VERDADEIRO_FALSO";
    return limpo in TIPOS ? limpo : null;
}

/** Escolhe um arquivo e abre o editor com o conteúdo dele. */
async function importarQuiz() {
    try {
        quizImportado = lerQuizDeArquivo(await escolherArquivo());
    } catch (e) {
        window.alert(e.message);
        return;
    }
    if (caminhoAtual() === "/novo") navegar();
    else location.hash = "#/novo";
}

// ---------------------------------------------------------------------------------------------------------------
// Criar e editar quiz
// ---------------------------------------------------------------------------------------------------------------

function novaQuestao(tipo = "UNICA") {
    const alternativas = tipo === "VERDADEIRO_FALSO"
        // Afirmações começam sem gabarito (null): o autor precisa dizer se cada uma é V ou F.
        ? [1, 2].map(() => ({ texto: "", correta: null }))
        : [1, 2, 3, 4].map(() => ({ texto: "", correta: false }));
    return { tipo, enunciado: "", explicacao: "", alternativas };
}

async function telaEditor(codigo) {
    let quiz = {
        titulo: "", tema: "", descricao: "", publico: true, embaralharQuestoes: false, embaralharAlternativas: false,
        questoes: [novaQuestao()],
    };
    const veioDeArquivo = !codigo && quizImportado != null;
    if (veioDeArquivo) {
        quiz = quizImportado;
        quizImportado = null;
    }
    if (codigo) {
        quiz = await api(`/api/quizzes/${codigo}/edicao`);
        if (!quiz.editavel) {
            return vazio("Este quiz não pode mais ser editado",
                "Outras pessoas já responderam, e mudar as questões agora deixaria o resultado delas sem sentido. "
                + "Você ainda pode mudar quem vê o quiz ou excluí-lo em Meus quizzes.",
                h("a", { class: "botao primario", href: "#/meus" }, "Voltar a Meus quizzes"));
        }
    }

    const lista = h("div", { class: "questoes" });
    const erro = h("p", { class: "erro", role: "alert", hidden: true });
    const salvar = h("button", { type: "submit", class: "botao primario" }, codigo ? "Salvar alterações" : "Salvar quiz");

    const texto = (objeto, chave, atributos, multilinha) => h(multilinha ? "textarea" : "input", {
        value: objeto[chave] || "", oninput: e => { objeto[chave] = e.target.value; }, ...atributos,
    });

    function desenhar() {
        lista.replaceChildren(...quiz.questoes.map(cartaoDaQuestao));
    }

    function cartaoDaQuestao(questao, indice) {
        const vf = questao.tipo === "VERDADEIRO_FALSO";
        const mover = (para) => {
            quiz.questoes.splice(para, 0, quiz.questoes.splice(indice, 1)[0]);
            desenhar();
        };

        const tipo = h("select", {
            "aria-label": "Tipo da questão",
            onchange: () => { mudarTipo(questao, tipo.value); desenhar(); },
        }, Object.entries(TIPOS).map(([valor, t]) => h("option", { value: valor, selected: valor === questao.tipo }, t.nome)));

        const alternativas = questao.alternativas.map((alternativa, i) => {
            const marcar = vf
                ? h("div", { class: "vf", role: "group", "aria-label": "Gabarito da afirmação" },
                    [[true, "V", "Verdadeira"], [false, "F", "Falsa"]].map(([valor, letra, nome]) => h("label", { title: nome },
                        h("input", {
                            type: "radio", name: `gabarito-${indice}-${i}`, checked: alternativa.correta === valor,
                            "aria-label": nome, onchange: () => { alternativa.correta = valor; },
                        }),
                        h("span", null, letra))))
                : h("input", {
                    type: questao.tipo === "UNICA" ? "radio" : "checkbox", name: `correta-${indice}`, class: "marca-correta",
                    checked: alternativa.correta, title: "Marcar como correta", "aria-label": `Alternativa ${i + 1} é correta`,
                    onchange: e => {
                        if (questao.tipo === "UNICA") questao.alternativas.forEach(a => { a.correta = false; });
                        alternativa.correta = e.target.checked;
                    },
                });
            return h("div", { class: "alternativa-edicao" },
                marcar,
                texto(alternativa, "texto", {
                    type: "text", maxLength: 500, placeholder: vf ? `Afirmação ${i + 1}` : `Alternativa ${i + 1}`,
                    "aria-label": vf ? `Afirmação ${i + 1}` : `Alternativa ${i + 1}`,
                }),
                h("button", {
                    type: "button", class: "icone", title: "Remover", "aria-label": "Remover",
                    disabled: questao.alternativas.length <= (vf ? 1 : 2),
                    onclick: () => { questao.alternativas.splice(i, 1); desenhar(); },
                }, "×"));
        });

        return h("section", { class: "cartao questao-edicao" },
            h("div", { class: "questao-topo" },
                h("strong", null, `Questão ${indice + 1}`),
                tipo,
                h("div", { class: "questao-botoes" },
                    h("button", { type: "button", class: "icone", title: "Mover para cima", "aria-label": "Mover para cima", disabled: indice === 0, onclick: () => mover(indice - 1) }, "↑"),
                    h("button", { type: "button", class: "icone", title: "Mover para baixo", "aria-label": "Mover para baixo", disabled: indice === quiz.questoes.length - 1, onclick: () => mover(indice + 1) }, "↓"),
                    h("button", { type: "button", class: "icone perigo", title: "Remover questão", "aria-label": "Remover questão", disabled: quiz.questoes.length === 1, onclick: () => { quiz.questoes.splice(indice, 1); desenhar(); } }, "×"))),
            texto(questao, "enunciado", { rows: 2, maxLength: 1000, placeholder: vf ? "Enunciado. Ex.: Julgue as afirmações sobre…" : "Enunciado da questão", "aria-label": "Enunciado" }, true),
            h("p", { class: "dica" }, vf
                ? "Escreva as afirmações e diga se cada uma é verdadeira (V) ou falsa (F)."
                : questao.tipo === "UNICA" ? "Marque a bolinha da alternativa correta."
                    : "Marque as caixas de todas as alternativas corretas. Só vale ponto quem marcar exatamente elas."),
            alternativas,
            questao.alternativas.length < 10 && h("button", {
                type: "button", class: "botao discreto",
                onclick: () => { questao.alternativas.push({ texto: "", correta: vf ? null : false }); desenhar(); },
            }, vf ? "+ Afirmação" : "+ Alternativa"),
            h("label", { class: "campo" }, h("span", null, "Explicação (opcional)"),
                texto(questao, "explicacao", { type: "text", maxLength: 1000, placeholder: "Aparece no resultado, depois que a pessoa responde" })));
    }

    async function enviar(evento) {
        evento.preventDefault();
        const pedido = montarPedido(quiz);
        if (pedido.problema) {
            erro.textContent = pedido.problema;
            erro.hidden = false;
            return;
        }
        erro.hidden = true;
        salvar.disabled = true;
        try {
            if (codigo) await api("/api/quizzes/" + codigo, { metodo: "PUT", corpo: pedido.quiz });
            else await api("/api/quizzes", { metodo: "POST", corpo: pedido.quiz });
            location.hash = "#/meus";
            recado(codigo ? "Alterações salvas." : "Quiz criado. Copie o link para compartilhar.");
        } catch (e) {
            erro.textContent = e.message;
            erro.hidden = false;
        } finally {
            salvar.disabled = false;
        }
    }

    desenhar();
    return h("form", { class: "editor", novalidate: true, onsubmit: enviar },
        cabecalho(codigo ? "Editar quiz" : "Criar quiz",
            !codigo && "Monte as questões aqui ou traga um quiz pronto de um arquivo.",
            !codigo && h("div", { class: "acoes" },
                h("button", { type: "button", class: "botao discreto", onclick: () => baixarArquivo("modelo.quiz.json", MODELO_DE_QUIZ) }, "Baixar modelo"),
                h("button", {
                    type: "button", class: "botao",
                    onclick: () => {
                        const temAlgo = quiz.titulo.trim() || quiz.questoes.some(q => q.enunciado.trim() || q.alternativas.some(a => a.texto.trim()));
                        if (!temAlgo || window.confirm("Importar um arquivo substitui o que você já preencheu aqui. Continuar?")) importarQuiz();
                    },
                }, "Importar arquivo"))),
        veioDeArquivo && h("p", { class: "nota" },
            `Quiz importado do arquivo, com ${plural(quiz.questoes.length, "questão", "questões")}. Confira e clique em Salvar quiz para ele entrar no site.`),
        h("section", { class: "cartao dados-quiz" },
            h("label", { class: "campo" }, h("span", null, "Título"),
                texto(quiz, "titulo", { type: "text", maxLength: 120, placeholder: "Ex.: Revisão de História, capítulo 3" })),
            h("label", { class: "campo" }, h("span", null, "Tema (opcional)"),
                texto(quiz, "tema", { type: "text", maxLength: 40, placeholder: "Ex.: História" }),
                h("small", null, "Usado para agrupar o desempenho de quem responde.")),
            h("label", { class: "campo inteiro" }, h("span", null, "Descrição (opcional)"),
                texto(quiz, "descricao", { rows: 2, maxLength: 500 }, true)),
            h("label", { class: "caixa inteiro" },
                h("input", { type: "checkbox", checked: quiz.publico, onchange: e => { quiz.publico = e.target.checked; } }),
                h("span", null, h("strong", null, "Publicar no Explorar"),
                    h("small", null, "Desmarcado, o quiz só abre para quem receber o link."))),
            h("label", { class: "caixa inteiro" },
                h("input", { type: "checkbox", checked: quiz.embaralharQuestoes, onchange: e => { quiz.embaralharQuestoes = e.target.checked; } }),
                h("span", null, h("strong", null, "Questões em ordem aleatória"),
                    h("small", null, "Cada pessoa recebe as questões numa ordem sorteada."))),
            h("label", { class: "caixa inteiro" },
                h("input", { type: "checkbox", checked: quiz.embaralharAlternativas, onchange: e => { quiz.embaralharAlternativas = e.target.checked; } }),
                h("span", null, h("strong", null, "Alternativas em ordem aleatória"),
                    h("small", null, "As alternativas (e as afirmações de verdadeiro ou falso) aparecem embaralhadas. Evite textos como \"todas as anteriores\".")))),
        lista,
        h("button", {
            type: "button", class: "botao largo tracejado",
            onclick: () => { quiz.questoes.push(novaQuestao(quiz.questoes.at(-1).tipo)); desenhar(); lista.lastElementChild.querySelector("textarea").focus(); },
        }, "+ Adicionar questão"),
        erro,
        h("div", { class: "barra-final" }, h("a", { class: "botao", href: "#/meus" }, "Cancelar"), salvar));
}

function mudarTipo(questao, tipo) {
    const eraVf = questao.tipo === "VERDADEIRO_FALSO";
    questao.tipo = tipo;
    // "Correta" e "verdadeira" não são a mesma coisa: ao trocar entre os dois mundos, o gabarito recomeça.
    if (eraVf !== (tipo === "VERDADEIRO_FALSO")) {
        questao.alternativas.forEach(a => { a.correta = eraVf ? false : null; });
    }
    if (tipo === "VERDADEIRO_FALSO") return;
    while (questao.alternativas.length < 2) questao.alternativas.push({ texto: "", correta: false });
    if (tipo === "UNICA") {
        // Só uma pode ficar marcada: mantém a primeira.
        const primeira = questao.alternativas.findIndex(a => a.correta);
        questao.alternativas.forEach((a, i) => { a.correta = i === primeira; });
    }
}

/** Limpa o que foi digitado e confere as mesmas regras que a API confere, para avisar antes de enviar. */
function montarPedido(quiz) {
    const limpo = {
        titulo: quiz.titulo.trim(), tema: (quiz.tema || "").trim(), descricao: (quiz.descricao || "").trim(),
        publico: quiz.publico,
        embaralharQuestoes: quiz.embaralharQuestoes === true, embaralharAlternativas: quiz.embaralharAlternativas === true,
        questoes: quiz.questoes.map(q => ({
            tipo: q.tipo, enunciado: q.enunciado.trim(), explicacao: (q.explicacao || "").trim(),
            // Linhas deixadas em branco são ignoradas.
            alternativas: q.alternativas.map(a => ({ texto: a.texto.trim(), correta: a.correta })).filter(a => a.texto),
        })),
    };
    if (!limpo.titulo) return { problema: "Dê um título ao quiz." };
    for (const [i, q] of limpo.questoes.entries()) {
        const n = `Questão ${i + 1}: `;
        const corretas = q.alternativas.filter(a => a.correta === true).length;
        if (!q.enunciado) return { problema: n + "escreva o enunciado." };
        if (q.tipo === "VERDADEIRO_FALSO") {
            if (!q.alternativas.length) return { problema: n + "escreva pelo menos uma afirmação." };
            if (q.alternativas.some(a => a.correta == null)) return { problema: n + "diga se cada afirmação é verdadeira (V) ou falsa (F)." };
            continue;
        }
        if (q.alternativas.length < 2) return { problema: n + "preencha pelo menos duas alternativas." };
        if (q.tipo === "UNICA" && corretas !== 1) return { problema: n + "marque qual alternativa é a correta." };
        if (q.tipo === "MULTIPLA" && corretas < 1) return { problema: n + "marque pelo menos uma alternativa correta." };
    }
    return { quiz: limpo };
}

// ---------------------------------------------------------------------------------------------------------------
// Responder um quiz
// ---------------------------------------------------------------------------------------------------------------

async function telaResponder(codigo) {
    const quiz = await api("/api/quizzes/" + codigo);
    /** O que foi marcado: id da alternativa -> true/false. */
    const respostas = new Map();
    const erro = h("p", { class: "erro", role: "alert", hidden: true });
    const enviar = h("button", { type: "submit", class: "botao primario" }, "Enviar respostas");
    const semConta = !sessao;
    const nome = semConta && h("input", {
        type: "text", name: "nome", maxLength: 80, required: true, autocomplete: "name",
        placeholder: "Como você quer aparecer para quem criou o quiz", value: lerDaAba(CHAVE_NOME) || "",
    });

    const questoes = quiz.questoes.map((questao, indice) => {
        const alternativas = questao.alternativas.map(alternativa => {
            if (questao.tipo === "VERDADEIRO_FALSO") {
                return h("div", { class: "afirmacao" },
                    h("span", null, alternativa.texto),
                    h("div", { class: "vf", role: "group", "aria-label": "Sua resposta" },
                        [[true, "V", "Verdadeiro"], [false, "F", "Falso"]].map(([valor, letra, nome]) => h("label", { title: nome },
                            h("input", { type: "radio", name: "a" + alternativa.id, "aria-label": nome, onchange: () => respostas.set(alternativa.id, valor) }),
                            h("span", null, letra)))));
            }
            const unica = questao.tipo === "UNICA";
            return h("label", { class: "opcao" },
                h("input", {
                    type: unica ? "radio" : "checkbox", name: "q" + questao.id,
                    onchange: e => {
                        if (unica) questao.alternativas.forEach(a => respostas.delete(a.id));
                        if (e.target.checked) respostas.set(alternativa.id, true);
                        else respostas.delete(alternativa.id);
                    },
                }),
                h("span", null, alternativa.texto));
        });
        return h("fieldset", { class: "cartao questao" },
            h("legend", null, h("span", { class: "numero" }, indice + 1), questao.enunciado),
            h("p", { class: "dica" }, TIPOS[questao.tipo].dica),
            alternativas);
    });

    async function mandar(evento) {
        evento.preventDefault();
        if (semConta && !nome.value.trim()) {
            erro.textContent = "Informe o seu nome para enviar as respostas.";
            erro.hidden = false;
            nome.focus();
            return;
        }
        const emBranco = quiz.questoes.filter(q => !q.alternativas.some(a => respostas.has(a.id))).length;
        if (emBranco && !window.confirm(`${plural(emBranco, "questão ficou", "questões ficaram")} em branco. Enviar mesmo assim?`)) return;

        erro.hidden = true;
        enviar.disabled = true;
        try {
            const resultado = await api(`/api/quizzes/${codigo}/tentativas`, {
                metodo: "POST",
                corpo: {
                    nome: semConta ? nome.value.trim() : undefined,
                    respostas: [...respostas].map(([alternativaId, valor]) => ({ alternativaId, valor })),
                },
            });
            if (resultado.chave) {
                resultadoSemConta = resultado;
                guardarNaAba(CHAVE_PENDENTE, { id: resultado.id, chave: resultado.chave });
                guardarNaAba(CHAVE_NOME, nome.value.trim());
                location.hash = "#/resultado-sem-conta";
            } else {
                location.hash = "#/resultado/" + resultado.id;
            }
        } catch (e) {
            erro.textContent = e.message;
            erro.hidden = false;
            enviar.disabled = false;
        }
    }

    return h("form", { class: "responder", novalidate: true, onsubmit: mandar },
        h("div", { class: "cabecalho" }, h("div", null,
            seloTema(quiz.tema),
            h("h1", null, quiz.titulo),
            quiz.descricao && h("p", { class: "sub" }, quiz.descricao),
            h("p", { class: "rodape" }, `${plural(quiz.questoes.length, "questão", "questões")} · por ${quiz.autor}`))),
        semConta && h("section", { class: "cartao sem-conta" },
            h("label", { class: "campo" }, h("span", null, "Seu nome"), nome,
                h("small", null, "Você está respondendo sem conta. Quem criou o quiz vai ver este nome junto com as suas respostas.")),
            h("p", { class: "rodape" }, "Já tem conta? ",
                h("a", { href: "#/entrar", onclick: () => lembrarDestino(caminhoAtual()) }, "Entre"),
                " para guardar o resultado no seu histórico.")),
        !semConta && !quiz.meu && h("p", { class: "rodape" }, "Quem criou o quiz vai ver o seu nome, o seu e-mail e as suas respostas."),
        quiz.meu && h("p", { class: "nota" }, "Este quiz é seu. Responder serve como teste: se você editar as questões depois, o seu resultado de teste é apagado."),
        questoes,
        erro,
        h("div", { class: "barra-final" }, enviar));
}

// ---------------------------------------------------------------------------------------------------------------
// Meus resultados
// ---------------------------------------------------------------------------------------------------------------

function linhaDeTentativa(t, quem) {
    const p = porcento(t.pontos, t.total);
    return h("a", { class: "cartao linha", href: "#/resultado/" + t.id },
        h("span", { class: "nota-circulo " + faixa(p) }, p + "%"),
        h("span", { class: "linha-texto" },
            h("strong", null, quem || t.titulo),
            h("small", null, `${quem && t.email ? t.email + " · " : ""}${numero(t.pontos)} de ${t.total} · ${data(t.feitaEm)}`)),
        !quem && seloTema(t.tema),
        quem && t.semConta && h("span", { class: "selo" }, "sem conta"),
        h("span", { class: "seta", "aria-hidden": "true" }, "›"));
}

async function telaResultados() {
    const tentativas = await api("/api/tentativas");
    if (!tentativas.length) {
        return h("div", null, cabecalho("Meus resultados"),
            vazio("Você ainda não respondeu nenhum quiz", "Quando responder, a nota e a análise dos erros ficam guardadas aqui.",
                h("a", { class: "botao primario", href: "#/" }, "Explorar quizzes")));
    }

    const pontos = tentativas.reduce((soma, t) => soma + t.pontos, 0);
    const total = tentativas.reduce((soma, t) => soma + t.total, 0);
    const melhor = Math.max(...tentativas.map(t => porcento(t.pontos, t.total)));

    const porTema = new Map();
    for (const t of tentativas) {
        const grupo = porTema.get(t.tema || "Sem tema") || { pontos: 0, total: 0 };
        grupo.pontos += t.pontos;
        grupo.total += t.total;
        porTema.set(t.tema || "Sem tema", grupo);
    }
    const temas = [...porTema].map(([tema, g]) => ({ tema, p: porcento(g.pontos, g.total), total: g.total }))
        .sort((a, b) => a.p - b.p);

    const numeroGrande = (valor, rotulo) => h("div", { class: "cartao indicador" }, h("strong", null, valor), h("span", null, rotulo));

    return h("div", null,
        cabecalho("Meus resultados", "Todos os quizzes que você já respondeu."),
        h("div", { class: "indicadores" },
            numeroGrande(tentativas.length, tentativas.length === 1 ? "quiz respondido" : "quizzes respondidos"),
            numeroGrande(porcento(pontos, total) + "%", "de acerto no geral"),
            numeroGrande(melhor + "%", "melhor nota")),
        temas.length > 1 && h("section", { class: "cartao temas" },
            h("h2", null, "Acerto por tema"),
            h("p", { class: "sub" }, "Do tema em que você mais erra para o que mais acerta."),
            temas.map(t => h("div", { class: "tema-linha" },
                h("span", null, t.tema),
                h("div", { class: "barra", role: "img", "aria-label": `${t.p}% de acerto` },
                    h("div", { class: "barra-cheia " + faixa(t.p), style: `width:${t.p}%` })),
                h("strong", null, t.p + "%")))),
        h("h2", { class: "titulo-secao" }, "Histórico"),
        h("div", { class: "lista" }, tentativas.map(t => linhaDeTentativa(t))));
}

// ---------------------------------------------------------------------------------------------------------------
// Análise de uma tentativa
// ---------------------------------------------------------------------------------------------------------------

function situacao(questao) {
    if (!questao.respondida) return { chave: "branco", nome: "Em branco" };
    if (questao.pontos >= 1) return { chave: "certa", nome: "Acertou" };
    if (questao.pontos > 0) return { chave: "parcial", nome: `Acertou em parte (${numero(questao.pontos)} ponto)` };
    return { chave: "errada", nome: "Errou" };
}

function alternativaCorrigida(questao, a) {
    if (questao.tipo === "VERDADEIRO_FALSO") {
        const acertou = a.valor === a.correta;
        const letra = v => v ? "V" : "F";
        return h("div", { class: "corrigida " + (acertou ? "ok" : "ruim") },
            h("span", { class: "sinal", "aria-hidden": "true" }, acertou ? "✓" : "✗"),
            h("span", { class: "corrigida-texto" }, a.texto),
            h("span", { class: "veredito" },
                a.valor == null ? "Em branco" : `Você: ${letra(a.valor)}`,
                !acertou && h("strong", null, ` · Certo: ${letra(a.correta)}`)));
    }
    const marcou = a.valor === true;
    const classe = a.correta ? (marcou ? "ok" : "faltou") : (marcou ? "ruim" : "");
    const veredito = a.correta
        ? (marcou ? "Você marcou · correta" : "Resposta correta")
        : (marcou ? "Você marcou · errada" : null);
    return h("div", { class: "corrigida " + classe },
        h("span", { class: "sinal", "aria-hidden": "true" }, a.correta ? "✓" : marcou ? "✗" : ""),
        h("span", { class: "corrigida-texto" }, a.texto),
        veredito && h("span", { class: "veredito" }, veredito));
}

async function telaResultado(id) {
    return desenharResultado(await api("/api/tentativas/" + id));
}

/** O resultado de quem respondeu sem conta: existe só enquanto esta página estiver aberta. */
function telaResultadoSemConta() {
    if (!resultadoSemConta || sessao) {
        return vazio("Este resultado não está mais disponível",
            "Quem responde sem conta vê o resultado uma vez só. Para guardar os próximos, crie uma conta.",
            h("a", { class: "botao primario", href: sessao ? "#/resultados" : "#/criar-conta" }, sessao ? "Meus resultados" : "Criar conta"));
    }
    return desenharResultado(resultadoSemConta);
}

function desenharResultado(t) {
    const semConta = !sessao && Boolean(t.chave);
    const p = porcento(t.pontos, t.total);
    const contagem = { certa: 0, parcial: 0, errada: 0, branco: 0 };
    t.questoes.forEach(q => { contagem[situacao(q).chave]++; });
    const errosOuBrancos = t.total - contagem.certa;

    const questoes = t.questoes.map((q, i) => {
        const s = situacao(q);
        return h("section", { class: "cartao questao analise " + s.chave, "data-situacao": s.chave },
            h("div", { class: "analise-topo" },
                h("span", { class: "numero" }, i + 1),
                h("span", { class: "situacao " + s.chave }, s.nome)),
            h("h2", null, q.enunciado),
            q.alternativas.map(a => alternativaCorrigida(q, a)),
            q.explicacao && h("p", { class: "explicacao" }, h("strong", null, "Explicação: "), q.explicacao));
    });
    const lista = h("div", { class: "questoes" }, questoes);

    const filtro = errosOuBrancos > 0 && errosOuBrancos < t.total && h("label", { class: "caixa" },
        h("input", {
            type: "checkbox",
            onchange: e => questoes.forEach(q => { q.hidden = e.target.checked && q.dataset.situacao === "certa"; }),
        }),
        h("span", null, "Mostrar só o que não acertei"));

    const parte = (n, um, varios, classe) => n > 0 && h("span", { class: "situacao " + classe }, plural(n, um, varios));
    const frase = !t.minha ? null
        : p === 100 ? "Gabaritou. Parabéns!"
        : p >= 70 ? "Bom resultado. Veja abaixo o que faltou."
        : p >= 40 ? "Dá para melhorar. Revise as questões marcadas abaixo."
        : "Vale revisar o conteúdo. As respostas certas estão abaixo.";

    // Convite para criar conta, mostrado uma vez logo que o resultado aparece.
    const convite = semConta && !t.conviteMostrado && h("dialog", { class: "popup", "aria-labelledby": "convite-titulo" },
        h("h2", { id: "convite-titulo" }, "Quer guardar este resultado?"),
        h("p", null, "Você respondeu sem conta, então este resultado some quando você sair desta página."),
        h("p", null, "Criando uma conta agora, ele vai para o seu histórico e você pode rever a análise quando quiser."),
        h("div", { class: "barra-final" },
            h("button", { type: "button", class: "botao", onclick: () => convite.close() }, "Agora não"),
            h("a", { class: "botao primario", href: "#/criar-conta" }, "Criar conta")));
    if (convite) {
        t.conviteMostrado = true;
        // Só dá para abrir depois que a tela estiver na página.
        setTimeout(() => { if (convite.isConnected) convite.showModal(); }, 0);
    }

    return h("div", { class: "resultado" },
        convite,
        h("section", { class: "cartao placar" },
            h("div", { class: "nota-circulo grande " + faixa(p) }, p + "%"),
            h("div", null,
                h("p", { class: "rodape" }, t.minha ? "Seu resultado em"
                    : `Resultado de ${t.respondente} (${t.semConta ? "sem conta" : t.email}) em`),
                h("h1", null, t.quiz.titulo),
                h("p", { class: "sub" }, `${numero(t.pontos)} de ${plural(t.total, "ponto", "pontos")} · ${data(t.feitaEm)}`),
                h("div", { class: "selos" },
                    parte(contagem.certa, "certa", "certas", "certa"),
                    parte(contagem.parcial, "em parte", "em parte", "parcial"),
                    parte(contagem.errada, "errada", "erradas", "errada"),
                    parte(contagem.branco, "em branco", "em branco", "branco")),
                frase && h("p", { class: "frase" }, frase))),
        semConta && h("p", { class: "nota" },
            "Este resultado não fica guardado: ao sair desta página, ele some. ",
            h("a", { href: "#/criar-conta" }, "Crie uma conta"), " ou ", h("a", { href: "#/entrar" }, "entre"),
            " para guardá-lo no seu histórico."),
        !t.quiz.disponivel && h("p", { class: "nota" }, t.questoes.length
            ? "Este quiz foi excluído por quem criou. O seu resultado continua guardado aqui, mas não dá mais para refazer."
            : "Este quiz foi excluído por quem criou. A nota continua guardada, mas a análise das questões não está mais disponível."),
        filtro,
        lista,
        h("div", { class: "barra-final" },
            semConta ? h("a", { class: "botao", href: "#/q/" + t.quiz.codigo }, "Refazer o quiz")
                : t.minha ? h("a", { class: "botao", href: "#/resultados" }, "Meus resultados")
                : h("a", { class: "botao", href: "#/respostas/" + t.quiz.codigo }, "Voltar ao relatório"),
            semConta ? h("a", { class: "botao primario", href: "#/criar-conta" }, "Criar conta e guardar")
                : t.minha && t.quiz.disponivel && h("a", { class: "botao primario", href: "#/q/" + t.quiz.codigo }, "Refazer o quiz")));
}

// ---------------------------------------------------------------------------------------------------------------
// Relatório de um quiz (para o autor)
// ---------------------------------------------------------------------------------------------------------------

const pct = valor => numero(valor) + "%";

/** As quatro situações de uma questão, na ordem em que aparecem nas barras. A cor nunca vai sozinha: tem legenda. */
const SITUACOES = [
    { chave: "certas", nome: "Acertaram", classe: "certa" },
    { chave: "parciais", nome: "Em parte", classe: "parcial" },
    { chave: "erradas", nome: "Erraram", classe: "errada" },
    { chave: "emBranco", nome: "Em branco", classe: "branco" },
];

function indicador(valor, rotulo, detalhe) {
    return h("div", { class: "cartao indicador" }, h("strong", null, valor), h("span", null, rotulo), detalhe && h("small", null, detalhe));
}

/** Colunas com quantas tentativas caíram em cada faixa de nota. Uma série só: sem legenda, o título já diz. */
function graficoDeFaixas(faixas) {
    const maior = Math.max(1, ...faixas.map(f => f.tentativas));
    return h("div", { class: "colunas", role: "img", "aria-label": "Tentativas por faixa de nota: " + faixas.map(f => `${f.rotulo}: ${f.tentativas}`).join(", ") },
        faixas.map(f => h("div", { class: "coluna", title: `${f.rotulo}: ${plural(f.tentativas, "tentativa", "tentativas")}` },
            h("div", { class: "coluna-area" },
                h("span", { class: "coluna-valor" }, f.tentativas),
                h("div", { class: "coluna-barra", style: `height:${(f.tentativas / maior) * 100}%` })),
            h("span", { class: "coluna-rotulo" }, f.rotulo))));
}

/** Barra empilhada de uma questão: quantas tentativas acertaram, acertaram em parte, erraram ou deixaram em branco. */
function barraDaQuestao(q, tentativas) {
    const partes = SITUACOES.filter(s => q[s.chave] > 0);
    return h("div", { class: "pilha", role: "img", "aria-label": SITUACOES.map(s => `${s.nome}: ${q[s.chave]}`).join(", ") },
        partes.map(s => h("div", {
            class: "pilha-parte " + s.classe, style: `flex-grow:${q[s.chave]}`,
            title: `${s.nome}: ${q[s.chave]} de ${tentativas} (${Math.round(q[s.chave] / tentativas * 100)}%)`,
        })));
}

function detalheDaQuestao(q, tentativas) {
    const vf = q.tipo === "VERDADEIRO_FALSO";
    const maior = Math.max(1, ...q.alternativas.map(a => a.marcadas));
    return h("div", { class: "detalhe-questao" },
        h("p", { class: "rodape" }, SITUACOES.map(s => `${s.nome}: ${q[s.chave]}`).join(" · ")),
        h("div", { class: "rolagem" }, h("table", { class: "tabela" },
            h("thead", null, h("tr", null,
                h("th", null, vf ? "Afirmação" : "Alternativa"),
                h("th", null, "Gabarito"),
                vf ? [h("th", { class: "num" }, "Marcaram V"), h("th", { class: "num" }, "Marcaram F")]
                    : h("th", { class: "larga" }, "Quantos marcaram"))),
            h("tbody", null, q.alternativas.map(a => h("tr", null,
                h("td", null, a.texto),
                h("td", null, vf ? (a.correta ? "Verdadeira" : "Falsa") : (a.correta ? "✓ Correta" : "")),
                vf ? [h("td", { class: "num" }, a.marcadas), h("td", { class: "num" }, a.falsas)]
                    : h("td", null, h("div", { class: "medidor" },
                        h("div", { class: "barra" }, h("div", { class: "barra-cheia neutra", style: `width:${a.marcadas / maior * 100}%` })),
                        h("span", null, `${a.marcadas} (${Math.round(a.marcadas / tentativas * 100)}%)`)))))))));
}

/** Um valor de célula de CSV: sempre entre aspas, e sem deixar texto digitado por outra pessoa virar fórmula. */
function celula(valor) {
    let texto = valor == null ? "" : typeof valor === "number" ? valor.toLocaleString("pt-BR", { maximumFractionDigits: 2, useGrouping: false }) : String(valor);
    if (typeof valor !== "number" && /^[=+\-@\t\r]/.test(texto)) texto = "'" + texto;
    return '"' + texto.replace(/"/g, '""') + '"';
}

function relatorioEmCsv(r) {
    const secoes = [
        [["Relatório do quiz", r.quiz.titulo], ["Tema", r.quiz.tema || ""], ["Gerado em", data(r.geradoEm)]],
        [["Resumo"],
            ["Tentativas", r.resumo.tentativas], ["Pessoas", r.resumo.pessoas], ["Questões", r.quiz.questoes],
            ["Média de acerto (%)", r.resumo.media], ["Mediana (%)", r.resumo.mediana],
            ["Melhor nota (%)", r.resumo.melhor], ["Pior nota (%)", r.resumo.pior], ["Média de pontos", r.resumo.mediaDePontos]],
        [["Distribuição das notas"], ["Faixa", "Tentativas"], ...r.faixas.map(f => [f.rotulo, f.tentativas])],
        [["Questões"], ["Nº", "Tipo", "Enunciado", "Acerto (%)", "Acertaram", "Em parte", "Erraram", "Em branco"],
            ...r.questoes.map(q => [q.numero, TIPOS[q.tipo].nome, q.enunciado, q.acerto, q.certas, q.parciais, q.erradas, q.emBranco])],
        [["Alternativas"], ["Questão", "Texto", "Gabarito", "Marcaram (ou julgaram V)", "Julgaram F"],
            ...r.questoes.flatMap(q => q.alternativas.map(a => [q.numero, a.texto,
                q.tipo === "VERDADEIRO_FALSO" ? (a.correta ? "Verdadeira" : "Falsa") : (a.correta ? "Correta" : ""),
                a.marcadas, q.tipo === "VERDADEIRO_FALSO" ? a.falsas : ""]))],
        [["Pessoas"], ["Nome", "E-mail", "Tem conta", "Tentativas", "Primeira nota (%)", "Última nota (%)", "Melhor nota (%)", "Média (%)", "Última tentativa"],
            ...r.pessoas.map(p => [p.nome, p.email || "", p.semConta ? "Não" : "Sim", p.tentativas, p.primeira, p.ultima, p.melhor, p.media, data(p.ultimaEm)])],
        [["Tentativas"], ["Pessoa", "E-mail", "Tem conta", "Pontos", "Total", "Nota (%)", "Data"],
            ...r.tentativas.map(t => [t.respondente, t.email || "", t.semConta ? "Não" : "Sim", t.pontos, t.total, porcento(t.pontos, t.total), data(t.feitaEm)])],
    ];
    // Ponto e vírgula e BOM: é o que o Excel em português espera para abrir direto com acentos e colunas certas.
    return "﻿" + secoes.map(linhas => linhas.map(l => l.map(celula).join(";")).join("\r\n")).join("\r\n\r\n") + "\r\n";
}

async function telaRespostas(codigo) {
    const r = await api(`/api/quizzes/${codigo}/relatorio`);
    const copiar = h("button", { type: "button", class: "botao primario", onclick: () => copiarLink(codigo) }, "Copiar link");
    if (!r.resumo.tentativas) {
        return h("div", null, cabecalho("Relatório: " + r.quiz.titulo),
            vazio("Ninguém respondeu ainda", "Envie o link do quiz. Quando alguém responder, o relatório aparece aqui.", copiar));
    }

    const n = r.resumo.tentativas;
    // Da mais errada para a mais acertada; empate mantém a ordem do quiz.
    const questoes = [...r.questoes].sort((a, b) => a.acerto - b.acerto || a.numero - b.numero);
    const exportar = () => {
        baixarArquivo(nomeDeArquivo(r.quiz.titulo).replace(/\.quiz\.json$/, "") + "-relatorio.csv", relatorioEmCsv(r), "text/csv;charset=utf-8");
        recado("Relatório baixado em CSV. Abre no Excel, no LibreOffice e no Google Planilhas.");
    };

    return h("div", { class: "relatorio" },
        cabecalho("Relatório: " + r.quiz.titulo,
            `${plural(n, "tentativa", "tentativas")} de ${plural(r.resumo.pessoas, "pessoa", "pessoas")} · gerado em ${data(r.geradoEm)}`,
            h("div", { class: "acoes nao-imprime" },
                h("button", { type: "button", class: "botao", onclick: exportar }, "Exportar CSV"),
                h("button", { type: "button", class: "botao", onclick: () => window.print() }, "Imprimir ou PDF"),
                copiar)),
        h("div", { class: "indicadores" },
            indicador(pct(r.resumo.media), "média de acerto", `${numero(r.resumo.mediaDePontos)} de ${plural(r.quiz.questoes, "ponto", "pontos")}`),
            indicador(pct(r.resumo.mediana), "mediana", "metade das tentativas ficou abaixo disso"),
            indicador(pct(r.resumo.melhor), "melhor nota", `pior: ${pct(r.resumo.pior)}`),
            indicador(n, n === 1 ? "tentativa" : "tentativas", plural(r.resumo.pessoas, "pessoa diferente", "pessoas diferentes"))),

        h("section", { class: "cartao bloco" },
            h("h2", null, "Distribuição das notas"),
            h("p", { class: "sub" }, "Quantas tentativas terminaram em cada faixa de acerto."),
            graficoDeFaixas(r.faixas)),

        h("section", { class: "cartao bloco" },
            h("h2", null, "Desempenho por questão"),
            h("p", { class: "sub" }, "Da questão mais errada para a mais acertada. Abra uma questão para ver o que foi marcado em cada alternativa."),
            h("div", { class: "legenda" }, SITUACOES.map(s => h("span", null, h("i", { class: "amostra " + s.classe }), s.nome))),
            questoes.map(q => h("details", { class: "linha-questao" },
                h("summary", null,
                    h("span", { class: "numero" }, q.numero),
                    h("span", { class: "linha-questao-texto" }, q.enunciado),
                    barraDaQuestao(q, n),
                    h("strong", { title: "Média de acerto na questão" }, pct(q.acerto))),
                detalheDaQuestao(q, n)))),

        h("section", { class: "cartao bloco" },
            h("h2", null, "Por pessoa"),
            h("p", { class: "sub" }, "Quantas vezes cada pessoa fez o quiz e como foi. Quem fez mais vezes aparece primeiro. "
                + "Quem tem conta é separado pelo e-mail; quem respondeu sem conta, só pelo nome que digitou (nomes iguais contam como a mesma pessoa)."),
            h("div", { class: "rolagem" }, h("table", { class: "tabela" },
                h("thead", null, h("tr", null,
                    h("th", null, "Pessoa"), h("th", null, "E-mail"), h("th", { class: "num" }, "Tentativas"), h("th", { class: "num" }, "Primeira"),
                    h("th", { class: "num" }, "Última"), h("th", { class: "num" }, "Melhor"), h("th", { class: "num" }, "Média"),
                    h("th", null, "Última tentativa"))),
                h("tbody", null, r.pessoas.map(p => h("tr", null,
                    h("td", null, p.nome),
                    h("td", null, p.semConta ? h("span", { class: "selo" }, "sem conta") : p.email),
                    h("td", { class: "num" }, p.tentativas), h("td", { class: "num" }, pct(p.primeira)),
                    h("td", { class: "num" }, pct(p.ultima)), h("td", { class: "num" }, pct(p.melhor)), h("td", { class: "num" }, pct(p.media)),
                    h("td", { class: "sem-quebra" }, dataCurta(p.ultimaEm)))))))),

        h("h2", { class: "titulo-secao nao-imprime" }, "Todas as tentativas"),
        h("p", { class: "sub nao-imprime", style: "margin:-8px 0 12px" }, "Abra uma tentativa para ver o que a pessoa marcou em cada questão."),
        h("div", { class: "lista nao-imprime" }, r.tentativas.map(t => linhaDeTentativa(t, t.respondente))));
}

// ---------------------------------------------------------------------------------------------------------------
// Minha conta
// ---------------------------------------------------------------------------------------------------------------

function telaMinhaConta() {
    /** Um cartão com formulário: cuida do erro, do botão travado e de chamar a ação. */
    function formulario(titulo, descricao, campos, botao, acao, classe = "") {
        const erro = h("p", { class: "erro", role: "alert", hidden: true });
        const form = h("form", { class: "cartao form-conta-item " + classe, novalidate: true, onsubmit: enviar },
            h("h2", null, titulo), descricao, campos, erro, h("div", null, botao));
        async function enviar(evento) {
            evento.preventDefault();
            erro.hidden = true;
            botao.disabled = true;
            try {
                await acao(Object.fromEntries(new FormData(form)), form);
            } catch (e) {
                erro.textContent = e.message;
                erro.hidden = false;
            } finally {
                botao.disabled = false;
            }
        }
        return form;
    }
    const campo = (rotulo, atributos, ajuda) => h("label", { class: "campo" },
        h("span", null, rotulo), h("input", { type: "text", required: true, ...atributos }), ajuda && h("small", null, ajuda));
    const senha = (rotulo, nome, nova, ajuda) => campo(rotulo, { type: "password", name: nome, maxLength: 72, autocomplete: nova ? "new-password" : "current-password" }, ajuda);

    const nome = formulario("Nome de exibição",
        h("p", { class: "sub" }, "É o nome que aparece nos seus quizzes e para quem criou os quizzes que você responde."),
        campo("Nome", { name: "nome", value: sessao.usuario.nome, maxLength: 80, autocomplete: "name" }),
        h("button", { type: "submit", class: "botao primario" }, "Salvar nome"),
        async dados => {
            if (!dados.nome.trim()) throw new Error("Informe o nome.");
            const usuario = await api("/api/eu", { metodo: "PUT", corpo: { nome: dados.nome.trim() } });
            guardarSessao({ ...sessao, usuario });
            document.getElementById("conta-nome").textContent = usuario.nome;
            recado("Nome atualizado.");
        });

    const trocar = formulario("Senha",
        h("p", { class: "sub" }, "Ao trocar, a conta é desconectada dos outros aparelhos em que estiver aberta."),
        [senha("Senha atual", "senhaAtual", false), senha("Nova senha", "novaSenha", true, "Pelo menos 8 caracteres."), senha("Repita a nova senha", "repetida", true)],
        h("button", { type: "submit", class: "botao primario" }, "Trocar senha"),
        async (dados, form) => {
            if (!dados.senhaAtual) throw new Error("Informe a senha atual.");
            if (dados.novaSenha.length < 8) throw new Error("A nova senha precisa de pelo menos 8 caracteres.");
            if (dados.novaSenha !== dados.repetida) throw new Error("As duas senhas novas não são iguais.");
            await api("/api/eu/senha", { metodo: "PUT", corpo: { senhaAtual: dados.senhaAtual, novaSenha: dados.novaSenha } });
            form.reset();
            recado("Senha trocada.");
        });

    const excluir = formulario("Excluir conta",
        [h("p", { class: "sub" }, "Apaga de vez, sem volta:"),
            h("ul", { class: "lista-simples" },
                h("li", null, "o seu cadastro (nome, e-mail e senha);"),
                h("li", null, "todos os quizzes que você criou, com as questões e as respostas que receberam;"),
                h("li", null, "todos os seus resultados.")),
            h("p", { class: "sub" }, "Quem respondeu um quiz seu continua vendo o próprio resultado, mas sem o seu nome.")],
        senha("Digite a sua senha para confirmar", "senha", false),
        h("button", { type: "submit", class: "botao perigo" }, "Excluir minha conta"),
        async dados => {
            if (!dados.senha) throw new Error("Informe a sua senha para confirmar.");
            if (!window.confirm("Excluir a conta e tudo o que é dela? Não dá para desfazer.")) return;
            await api("/api/eu/excluir", { metodo: "POST", corpo: { senha: dados.senha } });
            guardarSessao(null);
            location.hash = "#/entrar";
            recado("Conta excluída.");
        }, "zona-de-perigo");

    return h("div", { class: "minha-conta" },
        cabecalho("Minha conta", "Conectado como " + sessao.usuario.email),
        nome, trocar, excluir);
}

// ---------------------------------------------------------------------------------------------------------------
// Rotas
// ---------------------------------------------------------------------------------------------------------------

const ROTAS = [
    [/^\/$/, telaExplorar],
    [/^\/entrar$/, () => telaConta(false)],
    [/^\/criar-conta$/, () => telaConta(true)],
    [/^\/meus$/, telaMeus],
    [/^\/novo$/, () => telaEditor(null)],
    [/^\/editar\/(\w+)$/, telaEditor],
    [/^\/q\/(\w+)$/, telaResponder],
    [/^\/resultados$/, telaResultados],
    [/^\/resultado\/(\d+)$/, telaResultado],
    [/^\/resultado-sem-conta$/, telaResultadoSemConta],
    [/^\/respostas\/(\w+)$/, telaRespostas],
    [/^\/conta$/, telaMinhaConta],
];
const ROTAS_ABERTAS = ["/entrar", "/criar-conta"];

let navegacao = 0;

async function navegar() {
    const caminho = caminhoAtual();
    const aberta = ROTAS_ABERTAS.includes(caminho);
    // Abrir um quiz pelo link e ver o resultado logo depois funcionam sem conta; o resto pede login.
    const dispensaConta = aberta || /^\/q\/\w+$/.test(caminho) || caminho === "/resultado-sem-conta";
    if (!sessao && !dispensaConta) {
        lembrarDestino(caminho);
        location.replace("#/entrar");
        return;
    }
    if (sessao && aberta) {
        location.replace("#/");
        return;
    }

    document.getElementById("menu").hidden = !sessao;
    document.getElementById("conta").hidden = !sessao;
    document.getElementById("visitante").hidden = Boolean(sessao) || aberta;
    document.getElementById("conta-nome").textContent = sessao ? sessao.usuario.nome : "";
    for (const link of document.querySelectorAll("#menu a")) {
        link.classList.toggle("ativo", link.dataset.rota === caminho);
    }

    const tela = document.getElementById("tela");
    const estaVez = ++navegacao;
    tela.replaceChildren(h("p", { class: "carregando" }, "Carregando…"));
    let conteudo;
    try {
        const rota = ROTAS.map(([padrao, montar]) => [caminho.match(padrao), montar]).find(([achou]) => achou);
        conteudo = rota
            ? await rota[1](...rota[0].slice(1))
            : vazio("Página não encontrada", "O endereço pode estar errado.", h("a", { class: "botao primario", href: "#/" }, "Ir para o início"));
    } catch (e) {
        conteudo = vazio("Não deu certo", e.message, h("button", { type: "button", class: "botao", onclick: navegar }, "Tentar de novo"));
    }
    // Se a pessoa já foi para outra tela enquanto esta carregava, o resultado antigo é descartado.
    if (estaVez !== navegacao) return;
    tela.replaceChildren(conteudo);
    window.scrollTo(0, 0);
}

document.getElementById("sair").addEventListener("click", async () => {
    try {
        await api("/api/logout", { metodo: "POST" });
    } catch {
        // Mesmo sem conseguir avisar o servidor, a pessoa sai neste navegador.
    }
    guardarSessao(null);
    location.hash = "#/entrar";
});

document.querySelector("#visitante a[href='#/entrar']").addEventListener("click", () => {
    // Quem resolve entrar no meio de um quiz volta para ele depois do login.
    if (caminhoAtual().startsWith("/q/")) lembrarDestino(caminhoAtual());
});

window.addEventListener("hashchange", navegar);
navegar();
