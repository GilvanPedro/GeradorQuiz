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
let sessao = lerSessao();

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
        lembrarDestino(caminhoAtual());
        location.hash = "#/entrar";
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
    const erro = h("p", { class: "erro", role: "alert", hidden: true });
    const botao = h("button", { type: "submit", class: "botao primario largo" }, criando ? "Criar conta" : "Entrar");

    const campo = (rotulo, atributos, ajuda) => h("label", { class: "campo" },
        h("span", null, rotulo), h("input", { required: true, ...atributos }), ajuda && h("small", null, ajuda));

    const form = h("form", { class: "cartao form-conta", novalidate: true, onsubmit: enviar },
        h("h1", null, criando ? "Criar conta" : "Entrar"),
        h("p", { class: "sub" }, veioDeUmQuiz
            ? "Para responder o quiz que você recebeu, entre ou crie uma conta. É rápido."
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
            location.hash = "#" + (pegarDestino() || "/");
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
    const criar = h("a", { class: "botao primario", href: "#/novo" }, "Criar quiz");
    if (!quizzes.length) {
        return h("div", null, cabecalho("Meus quizzes"),
            vazio("Você ainda não criou nenhum quiz", "Monte as questões, copie o link e envie para quem quiser.", criar));
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
                h("a", { class: "botao", href: "#/respostas/" + q.codigo }, "Ver respostas"),
                h("a", { class: "botao", href: "#/q/" + q.codigo }, "Responder"),
                h("a", { class: "botao", href: "#/editar/" + q.codigo }, "Editar"),
                alternar,
                h("button", { type: "button", class: "botao discreto perigo", onclick: excluir }, "Excluir")));

        async function excluir() {
            const aviso = q.tentativas
                ? `Excluir "${q.titulo}"? O link para de funcionar. Quem já respondeu continua vendo o próprio resultado.`
                : `Excluir "${q.titulo}"? O link para de funcionar.`;
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
    let quiz = { titulo: "", tema: "", descricao: "", publico: true, questoes: [novaQuestao()] };
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
        cabecalho(codigo ? "Editar quiz" : "Criar quiz"),
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
                    h("small", null, "Desmarcado, o quiz só abre para quem receber o link.")))),
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
        const emBranco = quiz.questoes.filter(q => !q.alternativas.some(a => respostas.has(a.id))).length;
        if (emBranco && !window.confirm(`${plural(emBranco, "questão ficou", "questões ficaram")} em branco. Enviar mesmo assim?`)) return;

        erro.hidden = true;
        enviar.disabled = true;
        try {
            const resultado = await api(`/api/quizzes/${codigo}/tentativas`, {
                metodo: "POST",
                corpo: { respostas: [...respostas].map(([alternativaId, valor]) => ({ alternativaId, valor })) },
            });
            location.hash = "#/resultado/" + resultado.id;
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
            h("small", null, `${numero(t.pontos)} de ${t.total} · ${data(t.feitaEm)}`)),
        !quem && seloTema(t.tema),
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
    const t = await api("/api/tentativas/" + id);
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

    return h("div", { class: "resultado" },
        h("section", { class: "cartao placar" },
            h("div", { class: "nota-circulo grande " + faixa(p) }, p + "%"),
            h("div", null,
                h("p", { class: "rodape" }, t.minha ? "Seu resultado em" : `Resultado de ${t.respondente} em`),
                h("h1", null, t.quiz.titulo),
                h("p", { class: "sub" }, `${numero(t.pontos)} de ${plural(t.total, "ponto", "pontos")} · ${data(t.feitaEm)}`),
                h("div", { class: "selos" },
                    parte(contagem.certa, "certa", "certas", "certa"),
                    parte(contagem.parcial, "em parte", "em parte", "parcial"),
                    parte(contagem.errada, "errada", "erradas", "errada"),
                    parte(contagem.branco, "em branco", "em branco", "branco")),
                frase && h("p", { class: "frase" }, frase))),
        filtro,
        lista,
        h("div", { class: "barra-final" },
            t.minha
                ? h("a", { class: "botao", href: "#/resultados" }, "Meus resultados")
                : h("a", { class: "botao", href: "#/respostas/" + t.quiz.codigo }, "Voltar às respostas"),
            t.minha && t.quiz.disponivel && h("a", { class: "botao primario", href: "#/q/" + t.quiz.codigo }, "Refazer o quiz")));
}

// ---------------------------------------------------------------------------------------------------------------
// Respostas de um quiz (para o autor)
// ---------------------------------------------------------------------------------------------------------------

async function telaRespostas(codigo) {
    const tentativas = await api(`/api/quizzes/${codigo}/tentativas`);
    const copiar = h("button", { type: "button", class: "botao primario", onclick: () => copiarLink(codigo) }, "Copiar link");
    if (!tentativas.length) {
        return h("div", null, cabecalho("Respostas"),
            vazio("Ninguém respondeu ainda", "Envie o link do quiz para as pessoas responderem.", copiar));
    }
    const pontos = tentativas.reduce((soma, t) => soma + t.pontos, 0);
    const total = tentativas.reduce((soma, t) => soma + t.total, 0);
    return h("div", null,
        cabecalho(tentativas[0].titulo,
            `${plural(tentativas.length, "resposta", "respostas")} · média de ${porcento(pontos, total)}% de acerto. Abra uma resposta para ver o que a pessoa marcou.`,
            copiar),
        h("div", { class: "lista" }, tentativas.map(t => linhaDeTentativa(t, t.respondente))));
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
    [/^\/respostas\/(\w+)$/, telaRespostas],
];
const ROTAS_ABERTAS = ["/entrar", "/criar-conta"];

let navegacao = 0;

async function navegar() {
    const caminho = caminhoAtual();
    const aberta = ROTAS_ABERTAS.includes(caminho);
    if (!sessao && !aberta) {
        lembrarDestino(caminho);
        // Quem chega por um link de quiz provavelmente ainda não tem conta.
        location.replace(caminho.startsWith("/q/") ? "#/criar-conta" : "#/entrar");
        return;
    }
    if (sessao && aberta) {
        location.replace("#/");
        return;
    }

    document.getElementById("menu").hidden = !sessao;
    document.getElementById("conta").hidden = !sessao;
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

window.addEventListener("hashchange", navegar);
navegar();
