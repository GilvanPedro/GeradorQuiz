-- Excluir um quiz passa a apagar o quiz, as questões, as alternativas e as respostas. O que sobra é só o histórico
-- de quem respondeu: a tentativa guarda o nome do quiz e, na hora da exclusão, uma cópia da análise.

alter table tentativa add column quiz_titulo varchar(120);
alter table tentativa add column quiz_tema varchar(40);
alter table tentativa add column quiz_autor varchar(80);
-- A análise congelada (JSON), preenchida só quando o quiz é excluído.
alter table tentativa add column analise varchar(1000000);

update tentativa set
    quiz_titulo = (select q.titulo from quiz q where q.id = tentativa.quiz_id),
    quiz_tema   = (select q.tema from quiz q where q.id = tentativa.quiz_id),
    quiz_autor  = (select u.nome from quiz q join usuario u on u.id = q.autor_id where q.id = tentativa.quiz_id);

alter table tentativa alter column quiz_titulo set not null;
alter table tentativa alter column quiz_autor set not null;
-- Fica vazio depois que o quiz é excluído.
alter table tentativa alter column quiz_id drop not null;
