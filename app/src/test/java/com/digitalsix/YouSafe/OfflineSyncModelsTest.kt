package com.digitalsix.YouSafe

import com.digitalsix.YouSafe.network.ConfirmarSessaoRequest
import com.digitalsix.YouSafe.network.ColetaMobileRequest
import com.digitalsix.YouSafe.network.ParticipanteSessao
import com.digitalsix.YouSafe.network.SincronizarSessaoRequest
import com.digitalsix.YouSafe.utils.AcaoSessaoPendente
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSyncModelsTest {
    private val gson = Gson()

    @Test
    fun participanteOnlineSemNumeroApontamentoNaoMudaContratoAtual() {
        val participante = ParticipanteSessao(
            nfc = "ABC123",
            unidadeId = "unidade-uuid",
            horarioRegistro = "2026-06-15T12:00:00Z"
        )

        val json = gson.toJson(participante)

        assertFalse(json.contains("numero_apontamento"))
    }

    @Test
    fun sincronizacaoOfflineEnviaSerialEIdsDeCatalogo() {
        val request = SincronizarSessaoRequest(
            unidadeId = "unidade-uuid",
            moduloId = "modulo-uuid",
            treinamentoId = "treinamento-uuid",
            ginasticaLaboralId = null,
            dataInicio = "2026-06-15T12:00:00Z",
            dataFim = "2026-06-15T13:00:00Z",
            observacoes = "Treinamento offline",
            participantes = listOf(
                ParticipanteSessao(
                    nfc = "ABC123",
                    unidadeId = "unidade-uuid",
                    horarioRegistro = "2026-06-15T12:10:00Z",
                    numeroApontamento = 1
                )
            ),
            coletaMobile = ColetaMobileRequest(
                appVersion = "test",
                platform = "android",
                platformVersion = "test",
                deviceModel = "test",
                deviceManufacturer = "test",
                deviceId = "device-uuid"
            )
        )

        val json = gson.toJsonTree(request).asJsonObject

        assertEquals("unidade-uuid", json["unidade_id"].asString)
        assertEquals("treinamento-uuid", json["treinamento_id"].asString)
        assertTrue(json["participantes"].asJsonArray[0].asJsonObject.has("nfc"))
    }

    @Test
    fun acaoPendenteDeConfirmacaoMantemSessaoDoServidor() {
        val acao = AcaoSessaoPendente(
            localId = "confirmar-sessao-uuid",
            sessaoUuid = "sessao-uuid",
            tipo = "confirmar",
            criadaEmMs = 1L,
            instrutorUuid = "instrutor-uuid",
            confirmarRequest = ConfirmarSessaoRequest(
                dataFim = "2026-06-15T13:00:00Z",
                participantes = listOf(
                    ParticipanteSessao(
                        nfc = "ABC123",
                        unidadeId = "unidade-uuid",
                        horarioRegistro = "2026-06-15T12:10:00Z"
                    )
                ),
                coletaMobile = ColetaMobileRequest(
                    appVersion = "test",
                    platform = "android",
                    platformVersion = "test",
                    deviceModel = "test",
                    deviceManufacturer = "test",
                    deviceId = "device-uuid"
                )
            )
        )

        val json = gson.toJsonTree(acao).asJsonObject

        assertEquals("sessao-uuid", json["sessaoUuid"].asString)
        assertEquals("confirmar", json["tipo"].asString)
        assertTrue(json["confirmarRequest"].asJsonObject.has("participantes"))
    }
}
