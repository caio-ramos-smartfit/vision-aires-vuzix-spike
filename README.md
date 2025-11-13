# Vuzix Blade 2 – Camera Capture & Face Detection POC

Esta POC foi desenvolvida em **Kotlin**, **Jetpack Compose** e **CameraX** para rodar diretamente no **Vuzix Blade 2**.  
O objetivo do projeto é fazer **identificação automática do cliente** utilizando reconhecimento facial e exibir no visor do óculos as informações de treino do aluno.

A POC será construída até o resultado final, que consiste em:

✔ Detectar localmente se existe um rosto na câmera utilizando uma biblioteca de Face Detection (como ML Kit ou MediaPipe)  
✔ Capturar automaticamente a imagem somente quando um rosto for realmente detectado  
✔ Enviar essa imagem para a AWS Rekognition utilizando `SearchFacesByImage`  
✔ Identificar a pessoa através do `ExternalImageId` associado ao rosto  
✔ Consultar nossa API interna para recuperar as informações do aluno  
✔ Exibir esses dados diretamente no visor do Vuzix Blade 2  

Ou seja: **o fluxo final vai da detecção do rosto até a exibição inteligente das informações do aluno no visor**.

---

## Funcionamento atual

A versão atual do aplicativo já implementa:

- Inicialização da câmera com **CameraX**
- Exibição do preview no visor do Vuzix
- Atraso automático de 3 segundos
- Captura de uma foto
- Exibição da imagem capturada na tela

Essa etapa valida o funcionamento da câmera e prepara a base para as etapas de detecção facial e integração com o Rekognition.

---

## Como executar no Vuzix Blade 2

Para rodar o app no Vuzix Blade 2:

1. Ative o modo desenvolvedor:
   - Abra **Settings → About**
   - Faça **7 swipes** no touchpad lateral
   - Ative **ADB Debugging**

2. Conecte o Vuzix ao computador via USB  
   Aceite a permissão de USB Debugging no visor.

3. No Android Studio:
   - Selecione o dispositivo `Vuzix_Blade_2`
   - Clique em **Run ▶️**

O app será instalado automaticamente no óculos.

---

## Tecnologias utilizadas

- **Kotlin**
- **Jetpack Compose**
- **CameraX**
- **Android Studio**
- **Vuzix Blade 2 (Android customizado)**
- **ADB / Developer Mode**

---

Este projeto seguirá evoluindo até entregar a experiência completa de identificação facial e exibição das informações do aluno para o instrutor diretamente no visor do Vuzix Blade 2.
