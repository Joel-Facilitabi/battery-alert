# Battery Alert 🔋

App Android que avisa com som de alarme e vibração quando a bateria do celular cai abaixo de um nível que você escolhe.
Criado para quem usa o celular como chave do carro elétrico e não pode ficar sem bateria.

## Como instalar

1. No celular Android, abra a página **[Releases](../../releases/latest)** deste repositório.
2. Toque no arquivo **BatteryAlert-vX.apk** para baixar.
3. Abra o arquivo baixado. Se o Android pedir, permita **"Instalar apps desta fonte"** para o navegador ou gerenciador de arquivos.
4. Abra o app, escolha o nível de alerta e ative o **Monitoramento**.
5. Permita as notificações quando o app pedir.

> **Celular Samsung:** se aparecer "Aplicativo desconhecido bloqueado", vá em **Configurações > Segurança e privacidade > Bloqueador automático** e desligue durante a instalação. Depois pode religar.

> O Google Play Protect pode avisar que o app é de um desenvolvedor desconhecido. Isso é normal para apps fora da Play Store: toque em **"Instalar mesmo assim"**.

## Dica para não perder o alerta

Alguns celulares (Samsung, Xiaomi, Motorola) fecham apps em segundo plano para economizar bateria.
Vá em **Configurações > Apps > Battery Alert > Bateria** e escolha **"Sem restrições"**.

## Avisos no WhatsApp (opcional)

O app pode mandar os alertas como mensagem no seu próprio WhatsApp, o que é útil quando você está no WhatsApp Web e não olha o celular. Usa o serviço gratuito [CallMeBot](https://www.callmebot.com/blog/free-api-whatsapp-messages/), só para uso pessoal.

1. No app, toque em **Como obter a chave** e siga as instruções do CallMeBot: salvar o contato do bot e mandar para ele a mensagem `I allow callmebot to send me messages`.
2. O bot responde com a sua chave (APIKey). Se não chegar em 2 minutos, tente de novo no dia seguinte.
3. No app, preencha seu número com DDI e DDD (ex.: `+5511999998888`) e a chave, e toque em **Salvar e testar**.

## Limite superior de carga

Ative **Avisar quando carregar até** e escolha o nível (ex.: 90%). Com o celular no carregador, você recebe um aviso, e a mensagem no WhatsApp se estiver configurado, quando a carga chegar nesse nível.

## Requisitos

Android 8.0 ou mais recente. Não funciona no iPhone.
