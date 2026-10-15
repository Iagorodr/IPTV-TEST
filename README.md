# IPTV TEST

Player IPTV para Android TV / Google TV / Fire TV. Lê listas **Xtream Codes** e mostra filmes, séries e canais por categoria.

> Versão **0.2** — organiza a lista sozinho por gênero e plataforma (estilo Netflix), junta dublado/legendado num só cartão e escolhe a fonte mais estável de cada canal ao vivo.

## Instalar na TV (Downloader)

1. Instale o app **Downloader** (da AFTVnews) pela loja da TV e permita "instalar apps desconhecidos" para ele.
2. No Downloader, digite o código curto:

   **`2779349`**

   Se preferir o endereço completo: `github.com/Iagorodr/IPTV-TEST/releases/download/latest/iptv-test.apk`

3. Baixe e instale. Para atualizar, repita o passo 2 (o código e o link sempre apontam para a versão mais nova).

## Privacidade

- Servidor, usuário e senha da lista são digitados na própria TV e ficam **só nela** (armazenamento privado do app, sem backup na nuvem).
- Este repositório é público: **nunca** coloque aqui listas, usuários, senhas ou links de streaming.

## Como é compilado

O GitHub Actions compila o app a cada alteração na branch `main` e publica o APK no link acima (ver `.github/workflows/build.yml`).

## Estado da v0.2

- Login (servidor, usuário, senha) com validação da conta; catálogo baixado uma vez e guardado na TV (atualiza sozinho após 12 h)
- Organização automática: prateleiras por gênero e por plataforma (Netflix, Prime, Disney+...), "Lançamentos" e "Adicionados recentemente"; categorias adultas ficam ocultas
- Um cartão por título: dublado, legendado, 4K e cópias ficam juntos, com escolha de idioma/qualidade
- Séries com temporadas e episódios; "Continuar assistindo" e "Minha lista"
- Canais ao vivo: um cartão por canal (as dezenas de cópias viram "fontes"); botão **Fontes** no player com ranking de estabilidade, **Testar fontes**, fonte fixa ou automática e troca sozinha quando a imagem trava
- Player (Media3/ExoPlayer) com reserva grande para canais ao vivo, troca de áudio e legendas, ajustar/preencher tela e painel de diagnóstico das faixas
