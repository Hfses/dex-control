# DeX Control — Touchpad e Teclado para Samsung DeX

Aplicativo Android que transforma o celular em um **touchpad e teclado** para controlar
o **Samsung DeX** em um monitor externo. Feito para quem acha o mouse nativo do DeX
difícil de usar.

## Recursos

- **Touchpad**: arraste para mover o cursor no monitor do DeX, com sensibilidade ajustável
- **Cliques**: toque = clique esquerdo, toque longo = clique direito (menu de contexto), toque duplo
- **Scroll**: faixa lateral dedicada para rolagem vertical
- **Teclado**: digite texto em qualquer campo focado no DeX
- **Atalhos**: Copiar, Colar, Recortar e Selecionar tudo (Ctrl+C/V/X/A)
- **Sistema**: Voltar, Início, Recentes, Notificações e Captura de tela
- **Cursor visual**: uma seta é desenhada no monitor externo mostrando a posição atual

## Como instalar (APK pronto)

1. Vá na aba **[Releases](../../releases)** deste repositório
2. Baixe o arquivo `dex-control.apk` da versão mais recente **no seu celular Samsung**
3. Toque no arquivo baixado para instalar
   - Se o Android pedir, permita **"Instalar apps de fontes desconhecidas"** para o navegador/gerenciador de arquivos
4. Abra o app **DeX Control**

## Como usar

1. Instale o **[Shizuku](https://shizuku.rikka.app/)** e inicie o serviço
   (via Wireless Debugging — não precisa de root). Isso é necessário uma vez a cada
   reinício do celular.
2. Abra o **DeX Control**, vá na aba **Config** e toque em **"Conceder permissão"**
   para autorizar o Shizuku.
3. Conecte o celular a um monitor (cabo HDMI/USB-C ou Wireless DeX) e ative o **Samsung DeX**.
4. Use a aba **Touchpad** para mover o cursor real do DeX, clicar e rolar.
5. Para digitar: ative o serviço de acessibilidade (aba Config), clique em um campo de
   texto no monitor e use a aba **Teclado**.

## Como funciona

O controle do mouse (mover, clicar, rolar) usa o **Shizuku**, que executa com
identidade de shell (uid 2000) e possui a permissão `INJECT_EVENTS`. Via
`IInputManager` (obtido pelo binder do Shizuku), o app injeta eventos de mouse
(`SOURCE_MOUSE`) direcionados ao display do DeX com `setDisplayId`. Como são eventos
de mouse reais, o próprio Android desenha e move o ponteiro do sistema.

> **Por que Shizuku?** O `AccessibilityService` do Android é bloqueado pela Samsung
> para injetar gestos na tela do DeX. O Shizuku é o único caminho confiável sem root.

A digitação usa ações de acessibilidade (`ACTION_SET_TEXT`, `ACTION_COPY`, etc.)
sobre o campo de texto focado.

**Privacidade**: nenhum dado é coletado, armazenado ou enviado. O app funciona 100% offline.

## Compilar manualmente

Requisitos: JDK 17 e Android SDK (API 34).

```bash
gradle assembleDebug
# APK em: app/build/outputs/apk/debug/app-debug.apk
```

Ou abra a pasta do projeto no **Android Studio** e clique em Run.

## Requisitos

- Android 11 (API 30) ou superior
- Celular Samsung com suporte a DeX (linhas Galaxy S, Note, Z Fold, alguns tablets)

## Estrutura

```
app/src/main/java/com/dexcontrol/app/
├── MainActivity.kt        # Interface (Jetpack Compose): touchpad, teclado, sistema
└── DexControlService.kt   # Serviço de acessibilidade: cursor, gestos, texto
```
