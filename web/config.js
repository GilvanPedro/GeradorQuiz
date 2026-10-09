// Endereço da API. Na sua máquina usa a API local; publicado na Vercel, usa a do Render.
// Depois de criar o serviço no Render, troque a URL abaixo pela que ele mostrar.
const API_PRODUCAO = "https://quizlab-api.onrender.com";

const rodandoLocal = ["localhost", "127.0.0.1", ""].includes(window.location.hostname);
window.API_URL = rodandoLocal ? "http://localhost:8080" : API_PRODUCAO;
