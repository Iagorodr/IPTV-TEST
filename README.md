# IPTV TEST

Player IPTV para Android TV / Google TV / Fire TV. Lê listas **Xtream Codes** e mostra filmes, séries e canais por categoria.

> Versão **0.3** — o Início virou uma visão geral: jogos dos seus times favoritos no topo (buscados na internet, sem depender da aba "jogos do dia" da lista), Fórmula 1 / UFC / Tênis, destaque com filmes **e** séries. Mantém tudo da 0.2: organização por gênero/plataforma, dublado+legendado juntos e a fonte mais estável de cada canal.

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

## Novidades da v0.3

- **Times favoritos (até 5)**: Ajustes → Times favoritos, ou o botão no topo do Início. Escolha por país: Brasil (Série A e Série B) e a divisão principal de outros países (Inglaterra, Espanha, Itália, Alemanha, França, Portugal, Holanda, Bélgica, Turquia, Escócia, Argentina, México, EUA); a **seleção** do país também está na lista.
- **Jogos no topo do Início**: jogos de hoje (inclusive ao vivo e encerrados) e de amanhã; time sem jogo nesses dias mostra o próximo. Horário no fuso da TV, placar e minuto quando está rolando.
- **Onde assistir**: tocar no jogo abre os canais. Se o jogo está em "Jogos em Destaque" da sua lista (ou em canais de jogo avulso, como MLS), ele aparece primeiro; senão você vê os canais de esporte da lista (Premiere, SporTV, ESPN...) e um atalho de busca.
- **Outros esportes (só eventos)**: Fórmula 1, UFC e Tênis com o próximo evento; tocar mostra os canais da lista para aquele esporte.
- Início mais completo: jogos, destaque (filmes e séries alternados), esportes, continuar assistindo, seus canais, minha lista e prateleiras.

Fonte dos jogos: API pública (sem chave) do site da ESPN — não é oficial e não informa quem transmite no Brasil/Bélgica; serve para testar. Mais adiante troca por uma fonte com contrato e pelo nosso servidor.

## Estado da v0.2

- Login (servidor, usuário, senha) com validação da conta; catálogo baixado uma vez e guardado na TV (atualiza sozinho após 12 h)
- Organização automática: prateleiras por gênero e por plataforma (Netflix, Prime, Disney+...), "Lançamentos" e "Adicionados recentemente"; categorias adultas ficam ocultas
- Um cartão por título: dublado, legendado, 4K e cópias ficam juntos, com escolha de idioma/qualidade
- Séries com temporadas e episódios; "Continuar assistindo" e "Minha lista"
- Canais ao vivo: um cartão por canal (as dezenas de cópias viram "fontes"); botão **Fontes** no player com ranking de estabilidade, **Testar fontes**, fonte fixa ou automática e troca sozinha quando a imagem trava
- Player (Media3/ExoPlayer) com reserva grande para canais ao vivo, troca de áudio e legendas, ajustar/preencher tela e painel de diagnóstico das faixas
