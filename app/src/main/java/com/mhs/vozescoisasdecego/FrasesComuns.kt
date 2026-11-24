package com.mhs.vozescoisasdecego

/** Frases que um leitor de telas fala toda hora. Sao geradas aos poucos, com o celular parado, e ficam no cache. */
object FrasesComuns {
    val LISTA: List<String> = listOf(
        // papeis e estados
        "Botão", "Caixa de seleção", "Botão de opção", "Interruptor", "Caixa de edição", "Campo de texto", "Link",
        "Lista", "Item", "Menu", "Aba", "Guia", "Cabeçalho", "Imagem", "Ícone", "Janela", "Painel", "Barra de rolagem",
        "Controle deslizante", "Selecionado", "Não selecionado", "Marcado", "Desmarcado", "Ativado", "Desativado",
        "Ligado", "Desligado", "Expandido", "Recolhido", "Editável", "Desativado", "Indisponível", "Carregando",
        "Toque duas vezes para ativar", "Toque duas vezes e segure para ativar", "Toque duas vezes para editar",
        "Toque duas vezes para marcar", "Toque duas vezes para abrir", "Toque duas vezes para selecionar",
        // navegacao
        "Voltar", "Início", "Recentes", "Tela inicial", "Configurações", "Mais opções", "Menu de opções", "Pesquisar",
        "Fechar", "Abrir", "Cancelar", "OK", "Sim", "Não", "Salvar", "Aplicar", "Enviar", "Concluído", "Pronto",
        "Próximo", "Anterior", "Continuar", "Permitir", "Negar", "Compartilhar", "Copiar", "Colar", "Recortar",
        "Excluir", "Apagar", "Editar", "Adicionar", "Remover", "Atualizar", "Rolar para frente", "Rolar para trás",
        "Imagens e Vídeos", "Aplicativos", "Notificações", "Contatos", "Chamadas", "Mensagens", "Conversas", "Galeria",
        "Câmera", "Relógio", "Calendário", "Agenda", "Calculadora", "Telefone", "Navegador", "Arquivos", "Música",
        "Mercado Pago", "WhatsApp", "RHVoice", "Gmail", "Maps", "YouTube", "Play Store",
        // teclado
        "Espaço", "Enter", "Tecla apagar", "Ponto", "Vírgula", "Interrogação", "Exclamação", "Dois pontos",
        "Ponto e vírgula", "Arroba", "Barra", "Hífen", "Aspas", "Sublinhado", "Cifrão", "Porcentagem", "Mais", "Menos",
        "Igual", "Asterisco", "Parêntese", "Colchete", "Maiúsculas", "Minúsculas", "Teclado", "Emojis",
        // mensagens
        "Mensagem", "Digite uma mensagem", "Escrever mensagem", "Mensagem de voz", "Gravar mensagem de voz",
        "Enviada", "Entregue", "Lida", "Você", "Hoje", "Ontem", "Agora", "Responder", "Encaminhar", "Anexar",
        "Chamada de voz", "Chamada de vídeo", "Chamada perdida", "Atender", "Recusar", "Desligar",
        // tempo e sistema
        "Bateria", "Carregando bateria", "Wi-Fi", "Dados móveis", "Bluetooth", "Modo avião", "Não perturbe",
        "Volume", "Brilho", "Silencioso", "Vibrar", "Alarme", "Cronômetro", "Temporizador",
        // letras e algarismos (o TalkBack soletra)
        "um", "dois", "três", "quatro", "cinco", "seis", "sete", "oito", "nove", "zero",
    ) + ('a'..'z').map { it.toString() }
}
