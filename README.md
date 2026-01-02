# Proteus

**Симуляция искусственной жизни** в стиле [Tierra](https://en.wikipedia.org/wiki/Tierra_(computer_simulation)) (Thomas S. Ray, 1991).

Организмы — это самовоспроизводящиеся программы, живущие в общей памяти ("супе"). Они конкурируют за ресурсы, мутируют при копировании и эволюционируют.

## 🚀 Быстрый старт

```bash
# Сборка
mvn clean package -DskipTests

# Запуск симуляции (100K циклов, 1M ячеек памяти)
./sim.sh

# Или с параметрами
java -jar target/proteus-*.jar run --cycles 500000 --soup-size 100000
```

## 📊 Пример вывода

```
═══════════════════════════════════════
         SIMULATION COMPLETE           
═══════════════════════════════════════

⏱️  Time: 5 min 37 sec  |  Speed: 296 cycles/sec

👥 Population:
   Alive: 5 000  |  Total spawns: 6 558 349
   Deaths: 18 by errors, 6 553 332 by reaper

💾 Memory:
   Used: 93 312 / 1 000 000 cells (9,3%)
   Memory leak: 0 ✓

🧬 Evolution:
   Mutations: 113 344 total (0,017 per spawn)
```

## 📖 Документация

| Документ | Описание |
|----------|----------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Полная архитектура системы, дизайн-решения, roadmap |
| [docs/ISA_SPEC.md](docs/ISA_SPEC.md) | Спецификация набора инструкций (ISA v1.2) |
| [docs/ASM_SPEC.md](docs/ASM_SPEC.md) | Документация ассемблера |

## 🧬 Как это работает

### Организмы

Каждый организм — программа в 10-50 инструкций, которая:
1. Выделяет память для потомка (`ALLOCATE`)
2. Копирует себя (`COPY`) — здесь происходят **мутации**!
3. Регистрирует потомка (`SPAWN`)
4. Повторяет

### Эволюция

- **Мутации** при `COPY` создают разнообразие
- **Reaper** убивает старых/неэффективных
- Выживают те, кто быстрее размножается

### Паразитизм

- Организмы могут **читать** и **писать** в любую память
- Команда `SEARCH` ищет паттерны (сигнатуры других организмов)
- Хищники перезаписывают код жертв, ломая их репликацию

## 🔍 Debug Mode

Покадровый просмотр симуляции для понимания как всё работает:

```bash
# Записать 40 циклов с Adam'ом
java -jar proteus-*.jar debug --cycles 40

# С явным seed для воспроизводимости
java -jar proteus-*.jar debug --cycles 100 --seed 12345

# Записать с инъекцией своего организма
java -jar proteus-*.jar debug --cycles 40 --inject examples/parasite.asm --name "Predator"

# Показать только циклы с 50 по 70
java -jar proteus-*.jar debug --cycles 100 --from 50 --to 70

# Вывод в файл
java -jar proteus-*.jar debug --cycles 200 --output debug.txt

# Сохранить checkpoint
java -jar proteus-*.jar debug --cycles 40 --save checkpoint.bin

# Только сводка
java -jar proteus-*.jar debug --cycles 100 --summary
```

Пример вывода:
```
══════════════════════════════════════════════════════════════════════
CYCLE 77
══════════════════════════════════════════════════════════════════════

📋 Events:
  🐣 Spawn: org #1 from parent #0 at addr 14 (size 14)

👥 Organisms (2):
  Adam#0 @ 0-13 (size 14), IP=12, errors=0, parent=#-1
     R0-R7: [14, 0, 0, 14, 14, 14, 28, 0]
  Adam#1 @ 14-27 (size 14), IP=1, errors=0, parent=#0
     R0-R7: [0, 0, 0, 0, 14, 0, 0, 0]

💾 Memory (non-zero regions):
        0: 0x0280000E  MOVI R4, 14               [Adam#0 +0]
        1: 0x03E00000  GETADDR R7                [Adam#0 +1]
        ...
       12: 0x02000000  MOVI R0, 0                [Adam#0 +12] <<<
       13: 0x3003FFF4  JMP -12                   [Adam#0 +13]
       14: 0x0280000E  MOVI R4, 14               [Adam#1 +0]
       15: 0x03E00000  GETADDR R7                [Adam#1 +1] <<<
```

## 🔧 Ассемблер

Создавайте собственных организмов на текстовом ассемблере:

```asm
; Мой организм
start:
    GETADDR R7          ; Получить свой адрес
    MOVI R4, 14         ; Размер генома
    ALLOCATE R4, R3     ; Выделить память для потомка
    ; ... цикл копирования ...
    SPAWN R3, R4        ; Создать потомка
    JMP start           ; Повторить
```

```bash
# Ассемблирование
java -jar proteus-*.jar assemble myorg.asm -d
```

## 📂 Примеры

| Файл | Описание |
|------|----------|
| [examples/adam.asm](examples/adam.asm) | Базовый репликатор — "Адам" |
| [examples/parasite.asm](examples/parasite.asm) | Паразит — ищет и атакует через SEARCH+COPY |
| [examples/chaotic.asm](examples/chaotic.asm) | Хаотик — стреляет вслепую через STORE |

## ⚙️ Конфигурация

| Параметр | По умолчанию | Описание |
|----------|--------------|----------|
| `--soup-size` | 1,000,000 | Размер памяти (ячейки) |
| `--cycles` | 100,000 | Максимум циклов симуляции |
| `--mutation-rate` | 0.002 | Вероятность мутации при COPY |
| `--max-organisms` | 5,000 | Максимум живых организмов |
| `--seed` | random | Seed для воспроизводимости |

## 🗺️ Roadmap

- [x] Базовая симуляция (ISA v1.2, Position-Independent Code)
- [x] Ассемблер с метками и .word директивой
- [ ] Manual Deploy — инъекция организмов в работающую симуляцию
- [ ] Frame Recorder — покадровая запись для анализа
- [ ] Checkpoint/Resume — сохранение и восстановление состояния
- [ ] Web UI — визуализация soup в реальном времени
- [ ] Multiplayer — соревнование организмов разных игроков

## 📚 Вдохновение

- [Tierra](https://en.wikipedia.org/wiki/Tierra_(computer_simulation)) — Thomas S. Ray, 1991
- [Avida](https://en.wikipedia.org/wiki/Avida) — развитие идей Tierra
- [Core War](https://en.wikipedia.org/wiki/Core_War) — "война программ" в памяти

## 📜 Лицензия

MIT
