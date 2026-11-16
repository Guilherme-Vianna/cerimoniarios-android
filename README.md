# Cerimoniários para Android

App Android que abre o site do Cerimoniários (`web/`) num WebView e acrescenta:

- **Login nativo com Google**: o Google bloqueia o login dentro de WebViews, então o app escolhe a conta (Credential
  Manager) e entrega o token ao site por `window.CerimoniariosAndroid` (`auth/NativeBridge.kt` ↔
  `web/src/lib/native-app.ts`). A ponte só é exposta para a origem de `cerimoniarios.appUrl`.
- **Sobre** (ícone ⓘ): a Comunidade Santo Antônio, de Jerônimo Monteiro (ES).
- **Apoiar** (ícone ♥): contribuições únicas pela Google Play (R$ 2, 5, 10, 20 e 50). Qualquer uma remove os anúncios
  para sempre na conta Google.
- **Anúncios**: um banner AdMob pequeno no topo (longe da barra de abas do site), com classificação G. A pessoa pode
  desligá-lo em Apoiar → Anúncios. O formulário de consentimento (UMP) só aparece para quem vai ver anúncios.

Todas as telas dizem: *10% de toda a receita líquida do app (anúncios e contribuições, já descontadas as taxas do
Google) é enviado à Comunidade Santo Antônio.* Esse repasse é feito por você, manualmente; guarde os comprovantes.

## Configuração (`gradle.properties`)

| Propriedade | Valor |
| --- | --- |
| `cerimoniarios.appUrl` | Endereço do site (`https://cerimoniarios-gilt.vercel.app`) |
| `cerimoniarios.googleWebClientId` | Firebase → Authentication → Sign-in method → Google → *Web client ID* |
| `cerimoniarios.admobAppId` | AdMob → Apps → ID do app (`ca-app-pub-…~…`) |
| `cerimoniarios.admobBannerId` | AdMob → bloco de anúncios Banner (`ca-app-pub-…/…`) |

Builds de debug sempre usam os IDs de teste do AdMob. O build de release falha se faltar algum valor ou o
`keystore.properties`.

## Rodar

1. Instale o [Android Studio](https://developer.android.com/studio) e abra esta pasta (`android/`). Ele baixa o SDK e o
   Gradle e gera o `gradlew`.
2. Preencha `cerimoniarios.googleWebClientId`.
3. No [Google Cloud Console](https://console.cloud.google.com/apis/credentials) do projeto `cerimoniarios-7c2ee`,
   crie um *ID do cliente OAuth* do tipo **Android** com o pacote `com.cerimoniarios.app` e o SHA-1 da chave de debug
   (`./gradlew signingReport`). Sem isso, o login nativo retorna erro.
4. Rode o app num aparelho ou emulador com Google Play.

## Publicar na Play Store

1. **Chave de upload**: Build → Generate Signed App Bundle → crie um `.jks` fora do repositório e um
   `keystore.properties` aqui (ignorado pelo git):
   ```
   storeFile=/caminho/upload.jks
   storePassword=…
   keyAlias=upload
   keyPassword=…
   ```
2. **Play Console** (taxa única de US$ 25): crie o app (pt-BR, Gratuito), ative o Play App Signing e envie o
   `app-release.aab` (`./gradlew bundleRelease`) para um teste interno.
3. **SHA-1 da Play**: em Configuração → Integridade do app, copie o SHA-1 da *chave de assinatura do app* e da *chave de
   upload* e crie um cliente OAuth Android para cada uma (passo 3 acima). Sem isso, o login falha no app baixado da loja.
4. **Produtos**: Monetizar → Produtos → Produtos no app → crie `apoio_2`, `apoio_5`, `apoio_10`, `apoio_20` e
   `apoio_50` com esses preços e ative. Configure um perfil de pagamentos (conta bancária) para receber.
5. **AdMob**: crie o app (vinculado à Play), um bloco Banner e preencha os IDs. Em Privacidade e mensagens, crie a
   mensagem de consentimento (GDPR/LGPD) usada pelo UMP. Publique o `app-ads.txt` no domínio do site do desenvolvedor.
6. **Conteúdo do app**: política de privacidade (URL), Segurança dos dados (login Google, ID de publicidade, compras),
   Anúncios = Sim, público-alvo, classificação indicativa. Se o público incluir menores de 13 anos, a política Famílias
   se aplica; os anúncios já são limitados a classificação G, mas talvez seja preciso marcar as solicitações como
   direcionadas a crianças.
7. **Teste fechado**: contas pessoais novas precisam de 12 testadores por 14 dias antes de publicar em produção.

### Antes de publicar, ainda falta

- Texto real da comunidade em `app/src/main/res/values/strings.xml` (`about_community_history`, `about_community_details`,
  hoje entre colchetes).
- Definir `NEXT_PUBLIC_CONTACT_EMAIL` na Vercel (e-mail de contato da política de privacidade) e fazer deploy.
- Na Play Console, usar `https://cerimoniarios-gilt.vercel.app/privacidade` como política de privacidade e
  `https://cerimoniarios-gilt.vercel.app/excluir-conta` como link de exclusão de conta (Segurança dos dados). No app, a
  exclusão fica em Minha conta (menu da foto).
- Ícone: o ícone adaptativo replica `web/src/app/icon.svg`; a loja ainda pede um ícone de 512×512 (`web/public/icon-512.png`
  serve), a imagem de destaque de 1024×500 e as capturas de tela.
- O `applicationId` (`com.cerimoniarios.app`) não pode mudar depois do primeiro envio.
