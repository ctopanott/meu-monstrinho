# Meu Monstrinho 💗

**Alimente seu monstrinho.** App Android para acompanhar calorias, proteína, carboidrato, gordura, fibras e água, com o Rolinho, um monstrinho que muda de humor conforme o seu dia.

## Baixar

Abra no celular: **Releases → meu-monstrinho.apk** (versão mais nova em `releases/latest`).

## O que tem

- Registro de alimentos com tabela própria, busca online (Open Food Facts), código de barras, rótulo, receitas, favoritos e refeições salvas
- Metas calculadas pelos seus dados, déficit acumulado e previsão de 1 kg de gordura ajustada pelas pesagens
- Água, atividades (musculação, caminhada, corrida, natação, bicicleta)
- Ciclo menstrual, humor, energia e sono
- Monjaro: datas de aplicação, rodízio de locais, sintomas e relatório em PDF para a consulta
- Lembretes de refeições, água, proteína e aplicação
- Conquistas com acessórios para o Rolinho, resumo da semana e modo escuro

Os dados ficam só no celular. Valores dos alimentos são aproximados e não substituem orientação médica ou nutricional.

## Como funciona o build

A cada envio de arquivos para a branch `main`, o GitHub Actions compila o APK assinado (secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) e publica em Releases.
