# Relatório de Desenvolvimento: HelloVuzix Face Recognition

## 1. Visão Geral e Arquitetura

O "HelloVuzix" é um aplicativo Android para óculos inteligentes que implementa um pipeline de reconhecimento facial em tempo real. A arquitetura foi desenhada para ser sequencial e resiliente, garantindo uma experiência de usuário fluida desde a detecção até a exibição dos dados.

-   **Estrutura da UI**: O projeto utiliza uma `ComponentActivity` única que gerencia a transição entre dois estados principais da interface: a visualização da câmera e a tela de informações do usuário. A UI é construída inteiramente com **Jetpack Compose**, e o gerenciamento de estado é feito de forma reativa com `mutableStateOf`.

-   **Decisão Arquitetural (Detecção Híbrida)**: Optou-se por uma abordagem híbrida. O **Google ML Kit** é usado para a detecção de rostos diretamente no dispositivo, por ser rápido e eficiente em termos de recursos. Apenas após a confirmação de um rosto estável, a imagem é enviada para o **AWS Rekognition**, que realiza o reconhecimento (comparação) de fato, sendo um serviço mais robusto e preciso para essa finalidade.

## 2. Fluxo de Funcionamento (Pipeline)

O processo do aplicativo segue as seguintes etapas:

1.  **Detecção de Rosto (On-Device)**: A câmera é iniciada via **CameraX**. O analisador de imagens processa os frames com o **ML Kit** para detectar a presença de um rosto.

2.  **Critério de Estabilidade**: Para evitar capturas acidentais, um rosto precisa ser detectado continuamente por um limiar de 30 frames. Se o rosto for perdido, o contador é reiniciado.

3.  **Reconhecimento Facial (Cloud)**: Uma vez atingido o limiar, a imagem é capturada e enviada para o **AWS Rekognition**. O serviço compara a face com uma coleção pré-existente (`faceid-vision-aires-dev`).

4.  **Recuperação de Dados (API Externa)**: Se o Rekognition encontra uma correspondência, seu `externalImageId` é usado para consultar os dados do aluno na API da **Bioritmo** via **Retrofit**.

5.  **Exibição na Interface**: A UI transiciona da câmera para uma tela de informações, que exibe os dados do aluno (nome, programa, etc.) de forma clara e legível.

## 3. Desafios de Desenvolvimento e Soluções

Durante a configuração inicial e a adição de novas funcionalidades, o projeto enfrentou um severo conflito de dependências, resultando em uma cascata de erros de compilação. Este foi o principal desafio técnico.

-   **Problema Principal**: Incompatibilidade de versões da biblioteca padrão do Kotlin. Dependências mais novas (como o **Retrofit 3.0.0**) foram compiladas com versões mais recentes do Kotlin (ex: 2.1.0), enquanto o projeto estava configurado para usar uma versão anterior (1.9.24). Isso causou o erro `Module was compiled with an incompatible version of Kotlin` e a quebra de referências a funções básicas do Kotlin.

-   **Análise do Erro**: A causa raiz foi a adição de versões de bibliotecas que não respeitavam as versões do **Android Gradle Plugin** e do **Kotlin** definidas no projeto. Tentar forçar a versão do `kotlin-stdlib` manualmente apenas mascarou o problema, não o resolveu.

-   **Solução Aplicada**:
    1.  **Alinhamento de Versões**: Foi feita uma revisão completa das dependências. A versão do **Retrofit** foi ajustada para `2.11.0` e a do **Compose BOM** (Bill of Materials) para `2024.06.00`, garantindo compatibilidade total com o Kotlin `1.9.24` e o Android Gradle Plugin `8.4.0`.
    2.  **Limpeza de Conflitos**: Arquivos duplicados que definiam a mesma lógica de API (ex: `BioApi.kt` vs `BioritmoApi.kt`) foram eliminados para resolver erros de "Redeclaration".

## 4. Tecnologias e Bibliotecas Principais

-   **Linguagem**: Kotlin (`1.9.24`)
-   **Plugin Gradle**: Android Gradle Plugin (`8.4.0`)
-   **Interface**: Jetpack Compose (BOM `2024.06.00`)
-   **Câmera**: CameraX
-   **Detecção de Rosto**: Google ML Kit Face Detection
-   **Reconhecimento Facial**: AWS Rekognition
-   **Autenticação Nuvem**: AWS Cognito
-   **Networking**: Retrofit (`2.11.0`) & Gson
-   **Assincronia**: Kotlin Coroutines

## 5. Próximos Passos e Melhorias Sugeridas

-   **Botão de Reset**: Adicionar um controle na UI para reiniciar o processo de reconhecimento sem precisar fechar e reabrir o app.
-   **Feedback Visual Aprimorado**: Melhorar a interface de "carregamento" com mensagens mais específicas sobre cada etapa do pipeline (Detectando -> Reconhecendo -> Buscando dados).
-   **Cache de Dados**: Implementar um cache simples para os dados da Bioritmo, evitando chamadas repetidas à API para o mesmo usuário em um curto período.
-   **Tratamento de Erros na UI**: Exibir mensagens de erro mais amigáveis e específicas na tela quando a API da Bioritmo falhar ou o rosto não for encontrado no AWS Rekognition.
