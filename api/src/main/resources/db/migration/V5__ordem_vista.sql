-- Em quizzes com ordem aleatória, guarda o número que gerou a ordem que a pessoa viu. Com ele o resultado dela é
-- mostrado nessa mesma ordem, em vez da ordem original do autor. Vazio nos quizzes sem sorteio e nas tentativas
-- antigas.
alter table tentativa add column ordem_semente bigint;
