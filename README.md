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

1. Conecte o celular a um monitor (cabo HDMI/USB-C ou Wireless DeX) e ative o **Samsung DeX**
2. Abra o **DeX Control** no celular e toque em **"Ativar serviço"**
   - Você será levado às configurações de Acessibilidade — ative **"DeX Control — Touchpad e Teclado"**
3. Uma seta de cursor aparecerá no monitor do DeX
4. Use a aba **Touchpad** para mover o cursor, clicar e rolar
5. Para digitar: clique em um campo de texto no monitor e use a aba **Teclado**

## Como funciona

O app usa um **Serviço de Acessibilidade** do Android (`AccessibilityService`) com
`dispatchGesture` direcionado ao display externo (`setDisplayId`), o que permite
injetar toques, arrastos e rolagens diretamente no monitor do DeX. A digitação usa
ações de acessibilidade (`ACTION_SET_TEXT`, `ACTION_COPY`, `ACTION_PASTE`, etc.)
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
