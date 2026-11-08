# Reconhecimento facial no Android

O formulário mostra nome, CPF, setor e nível antes das opções de identificação. **Adicionar NFC** exibe o campo para digitar ou ler o crachá. **Cadastrar facial** abre a captura automática com consentimento. É possível selecionar NFC, facial ou ambos. O botão Cadastrar funcionário fica desabilitado sem método selecionado ou enquanto algum método selecionado estiver incompleto. Selecionar os dois exige NFC preenchido e foto salva.

A captura facial acontece antes de qualquer POST de funcionário. CameraX e ML Kit verificam o enquadramento no círculo e capturam depois de 900 ms de estabilidade, sem prova de vida. A imagem é orientada, reduzida, validada e salva no armazenamento privado excluído de backup. A tela retorna ao formulário com **Foto salva para o cadastro**. Nenhum rosto é enviado para a API nessa etapa. Cancelar ou negar consentimento não habilita o cadastro facial; pode-se tentar novamente ou desmarcar facial e cadastrar apenas com NFC.

O aceite é registrado no momento real de aceitação e preservado até o envio. O texto `R.string.face_consent_text` continua como minuta para revisão jurídica. Alterar nome, CPF ou unidade invalida a foto e exige nova captura/aceite. Desmarcar facial, substituir a foto, concluir o cadastro ou sair da conta sem envio pendente apaga o arquivo anterior.

Ao tocar em Cadastrar funcionário, o app valida novamente os métodos e a foto antes de criar o funcionário. Envia `nfc: null` quando NFC não foi selecionado. Após receber o `public_id`, chama o cadastro facial usando a foto e o horário do aceite. Exibe sucesso final somente depois de concluir todos os métodos selecionados. Não há NFC fictício.

**Limite do contrato atual:** criação do funcionário e cadastro do rosto são requisições separadas. Uma falha na segunda pode deixar os dados do funcionário salvos na API sem vínculo facial. O app mantém o andamento, o UUID e a foto em rascunho privado por usuário, bloqueia alterações dos dados já enviados e oferece Concluir cadastro facial, sem criar outro funcionário. Para garantir que nunca exista um funcionário sem os métodos no servidor, inclusive sob falha de rede, a API precisaria oferecer uma operação única com confirmação/rollback.

Quando o resultado da criação é incerto (queda de conexão, falha de gateway ou resposta sem UUID), o app oferece Verificar cadastro. Consulta `GET /funcionarios?unidade_id={uuid}`, confere CPF, nome e NFC quando selecionado, e só então retoma usando o UUID encontrado. Sem correspondência, libera uma nova tentativa; em resultados conflitantes, não atribui o rosto automaticamente. Rascunhos não pertencem a outra conta e não são incluídos em backup.

O ID retornado pelo cadastro facial continua salvo em `face_enrollment_records` para futura gestão/exclusão. A rota DELETE continua disponível no serviço, sem tela de gerenciamento nesta etapa. Reinstalar/limpar os dados pode perder essas referências locais; a gestão central deve conservar os IDs.

Antes de iniciar a sessão, escolha NFC ou facial. O método fica fixo na sessão local e é preservado ao retomá-la, inclusive na segunda batida do treinamento. Sessões anteriores continuam em NFC. O método não é enviado como novo campo na criação da sessão, pois esse campo não foi especificado no contrato.

Na presença facial, **Iniciar reconhecimento facial** abre uma câmera ativa dentro do app. A prévia permanece aberta durante as validações e as chamadas à API. Não há botão de tirar foto nem aplicativo de câmera externo. ML Kit verifica um rosto por vez, posição, distância, ângulo e estabilidade antes de capturar um quadro automaticamente. CameraX compartilha a transformação da prévia com o detector para alinhar o rosto ao círculo.

O app envia somente `file` ao check-in, uma tentativa por vez. Para `BELOW_THRESHOLD`, aguarda pelo menos 1,8 s antes de rearmar o enquadramento de 900 ms, com até quatro tentativas por ciclo. Ao atingir o limite, pausa e permite Retomar reconhecimento. `NO_ENROLLMENT` e `NO_ELIGIBLE_ENROLLMENTS` pausam sem repetição automática. Não há presença manual nem prova de vida. JPEG/PNG são validados com limite de 8 MB, orientação normalizada e arquivo temporário apagado após cada operação.

Quando reconhece, mostra o nome e a confirmação; após a mensagem de sucesso, exige ausência de rostos por 800 ms antes de liberar a próxima pessoa. Isso evita reenviar o mesmo rosto continuamente. A API continua responsável por persistir a presença e sua deduplicação. A lista autoritativa é atualizada ao voltar para a sessão, mesmo após cancelar a leitura. O reconhecimento fica vinculado ao UUID e à batida que abriram a câmera; mudanças locais de sessão/batida bloqueiam novos envios.

É possível pausar/retomar a leitura. Ao ir para o segundo plano, a câmera segue o ciclo de vida Android e as novas tentativas ficam suspensas. Uma requisição já iniciada pode terminar. Em resultado incerto, falha de gateway/servidor ou restauração após envio interrompido, o reconhecimento automático fica bloqueado até atualizar as presenças e retomar explicitamente. O app não afirma reconhecimento em resposta incompleta e não reenvia a requisição automaticamente. Não há permissão de galeria/armazenamento; NFC permanece opcional para instalar o app.

HTTP 422 mostra o motivo na câmera ativa e controla a próxima tentativa. **Sem reconhecimento, não há presença. Não existe registro manual nem troca para NFC dentro da sessão facial.** A listagem aceita os métodos retornados pelo servidor para exibição, inclusive registros históricos com `manual`.

O check-in facial já grava a presença no servidor. O app usa GET para atualizar a lista, filtrada pela batida atual. Não transforma os reconhecimentos em participantes NFC nem os reenvia na confirmação/início do treinamento. Essas operações existentes são chamadas com a lista NFC vazia; é necessário validar com a API real que conservam as presenças já gravadas pelo check-in. Reconhecimento exige internet; o app não cria sessões faciais offline nem guarda fotos para sincronização posterior. Em falha de conexão com resultado incerto, atualize a lista antes de repetir a captura.

## Validação

Verificação automatizada: `sh gradlew :app:testTagsafeStagingDebugUnitTest :app:assembleTagsafeStagingDebug --offline`.

Os testes cobrem o enquadramento e tempo de captura automática, a política da sessão ativa (intervalo, limite, pausa e resultado incerto), além dos quatro contratos HTTP com respostas simuladas, multipart, UUID público, timestamp de consentimento, match/422 e persistência do método. Não acessam a API real nem usam credenciais.

Validar em aparelho com a API de staging:

- Testar NFC somente, facial somente e ambos. Sem opção ou com método selecionado incompleto, o botão deve ficar desabilitado.
- Capturar o rosto antes de cadastrar: confirmar retorno com foto salva, nenhum POST durante a captura e `nfc: null` no modo somente facial. Recusar/cancelar não deve habilitar a facial.
- Alterar nome, CPF e unidade após a captura: exigir nova foto. Digitar/ler NFC com facial selecionada também deve funcionar.
- Simular falha no vínculo facial e reabrir o app: retomar pelo mesmo UUID sem duplicar o funcionário. Simular resposta incerta da criação e conferir a reconciliação por unidade/CPF/nome/NFC.
- Conferir que ausência de rosto, mais de uma pessoa, rosto distante, inclinado ou fora do círculo impedem a captura.
- Negar a permissão de câmera, voltar das configurações, suspender/retomar o app e testar falha de câmera/envio com nova tentativa.
- Fechar e reabrir uma sessão facial e realizar as duas batidas, conferindo método fixo e contagem por batida.
- Iniciar reconhecimento facial, manter o rosto no círculo e conferir captura automática sem botão de disparo; confirmar nome e presença no servidor.
- Manter o rosto após sucesso: não deve haver novos envios. Sair do enquadramento e aproximar outra pessoa: deve liberar a próxima leitura.
- Testar quatro tentativas abaixo do limiar, pausa/retomada, background, rotação/recriação, erro 500 e perda de conexão após envio. Não reenviar automaticamente resultado incerto.
- Voltar à sessão e confirmar a atividade: conferir que a lista e a presença gravada continuam corretas na batida atual.
- Enviar rosto desconhecido: exibir motivo, controlar as novas tentativas e manter a lista sem nova presença.
- Testar câmera cancelada, foto acima de 8 MB, aparelho sem câmera disponível, falta de conexão e respostas 400/403/404/500.
- Conferir persistência local do ID do cadastro para futura exclusão; não há gerenciamento de rostos na interface nesta etapa.
- Conferir que a sessão NFC continua funcionando, inclusive offline, e ignora captura facial.
