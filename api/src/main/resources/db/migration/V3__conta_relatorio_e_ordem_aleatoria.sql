-- Último acesso: depois de 5 anos sem entrar, o e-mail fica livre para uma conta nova.
-- Quem já tem conta começa a contar a partir de agora.
alter table usuario add column ultimo_acesso_em timestamp with time zone;
update usuario set ultimo_acesso_em = current_timestamp;
alter table usuario alter column ultimo_acesso_em set not null;

-- Opções do autor: sortear a ordem das questões e/ou das alternativas a cada vez que alguém abre o quiz.
alter table quiz add column embaralhar_questoes boolean default false not null;
alter table quiz add column embaralhar_alternativas boolean default false not null;

-- Quem criou o quiz respondido. Sem chave estrangeira de propósito: serve para atualizar o nome do autor no
-- histórico dos outros quando ele muda de nome, e para tirar o nome quando ele exclui a conta.
alter table tentativa add column quiz_autor_id bigint;
update tentativa set quiz_autor_id = (select q.autor_id from quiz q where q.id = tentativa.quiz_id);
create index tentativa_quiz_autor_idx on tentativa (quiz_autor_id);
