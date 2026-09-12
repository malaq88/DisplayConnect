package com.example.displayconnect.routing

object NavigationText {
    fun instruction(english: String, language: String): String {
        if (language == "en") return english
        return when (english) {
            "Arrive at destination" -> "Você chegou ao destino"
            "Start route" -> "Inicie o percurso"
            "Turn right" -> "Vire à direita"
            "Turn left" -> "Vire à esquerda"
            "Slight right" -> "Mantenha-se à direita"
            "Slight left" -> "Mantenha-se à esquerda"
            "Sharp right" -> "Curva fechada à direita"
            "Sharp left" -> "Curva fechada à esquerda"
            "Continue straight", "Continue" -> "Siga em frente"
            "Uturn", "U-turn" -> "Faça o retorno"
            "Roundabout", "Rotary", "Roundabout turn" -> "Entre na rotatória"
            "Exit roundabout", "Exit rotary" -> "Saia da rotatória"
            "Merge" -> "Entre na via"
            "Fork" -> "Siga na bifurcação"
            "On ramp" -> "Entre no acesso"
            "Off ramp" -> "Saia pelo acesso"
            "End of road" -> "Fim da via"
            "New name", "Notification" -> "Continue na via"
            else -> english
        }
    }
}
