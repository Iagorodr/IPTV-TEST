# IPTV TEST

Player IPTV para Android TV / Google TV / Fire TV. Lê listas **Xtream Codes** e mostra filmes, séries e canais por categoria.

> Versão **0.1** — serve para provar que instala e reproduz bem na box. Ainda sem organização por gênero, perfis ou conta.

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

## Estado da v0.1

- Login (servidor, usuário, senha) com validação da conta
- Abas Filmes / Séries / Canais com as categorias do próprio provedor (categorias adultas ficam ocultas)
- Séries com temporadas e episódios
- Player (Media3/ExoPlayer) com troca de áudio e legendas, ajustar/preencher tela, avanço de 10 s e painel de diagnóstico das faixas
