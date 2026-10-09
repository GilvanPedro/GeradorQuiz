-- Quem recebe o link pode responder sem conta, só informando um nome. Nessas tentativas não há usuário.
alter table tentativa alter column usuario_id drop not null;
alter table tentativa add column convidado_nome varchar(80);
-- SHA-256 da chave que o navegador do convidado recebe ao responder. Com ela, se a pessoa criar uma conta (ou
-- entrar) logo em seguida, a tentativa passa a ser dela.
alter table tentativa add column chave_hash varchar(64);
