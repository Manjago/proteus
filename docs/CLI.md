# Proteus CLI Reference

Полная документация по командам Proteus.

## Обзор

```bash
proteus <command> [options]
```

Доступные команды:
- `run` — запуск симуляции
- `checkpoint` — работа с checkpoint файлами
- `assemble` — ассемблирование .asm файлов
- `analyze` — анализ сохранённых данных
- `info` — информация о программе

## proteus run

Основная команда для запуска симуляции.

### Синтаксис

```bash
proteus run [options]
```

### Опции

| Опция | Описание | По умолчанию |
|-------|----------|--------------|
| `-i, --inject FILE` | Инъекция организма из .asm файла | *обязательно* (если нет --resume) |
| `-n, --name NAME` | Имя для инъецированного организма | имя файла без .asm |
| `-c, --cycles N` | Количество циклов (0 = бесконечно) | 0 |
| `-s, --soup-size N` | Размер памяти (ячеек) | 100000 |
| `--seed N` | Seed для RNG (для воспроизводимости) | random |
| `--max-organisms N` | Максимум живых организмов | 1000 |
| `-m, --mutation-rate R` | Вероятность мутации (0.0-1.0) | 0.002 |
| `--resume FILE` | Продолжить из checkpoint | - |
| `--save FILE` | Сохранить checkpoint после run | - |
| `--checkpoint-interval N` | **Auto-save каждые N циклов** (требует --save) | 0 (выкл) |
| `--report-interval N` | Интервал отчёта (циклов) | 1000 |
| `-q, --quiet` | Тихий режим (минимум вывода) | false |
| `-f, --config FILE` | Конфигурационный файл (HOCON) | - |

### Debug опции

| Опция | Описание |
|-------|----------|
| `--debug FILE` | Включить покадровую запись (или `-` для stdout) |
| `--from N` | Показать циклы начиная с N |
| `--to N` | Показать циклы до N (включительно) |
| `--summary` | Только сводная таблица |

### Примеры

```bash
# Простой запуск
proteus run --inject ancestor.asm --cycles 100000

# С seed для воспроизводимости
proteus run --inject ancestor.asm --cycles 100000 --seed 12345

# Большая симуляция с auto-checkpoint (РЕКОМЕНДУЕТСЯ!)
proteus run --inject ancestor.asm \
    --cycles 1000000 \
    --soup-size 1000000 \
    --max-organisms 5000 \
    --save result.mv \
    --checkpoint-interval 50000  # Сохранять каждые 50K циклов

# Продолжить из checkpoint
proteus run --resume checkpoint.mv --cycles 50000 --save checkpoint.mv

# Debug режим
proteus run --inject ancestor.asm --cycles 100 --debug output.txt

# Debug в stdout
proteus run --inject ancestor.asm --cycles 40 --debug -

# Debug с фильтрацией
proteus run --inject ancestor.asm --cycles 100 \
    --debug output.txt --from 50 --to 70
```

### Поведение

1. **Свежий старт** (`--inject` без `--resume`):
   - Создаётся пустой мир
   - Инъецируется организм из файла
   - Запускается симуляция

2. **Продолжение** (`--resume`):
   - Загружается состояние из checkpoint
   - RNG восстанавливается (детерминированный resume)
   - CLI опции (--max-organisms, --cycles и др.) переопределяют значения из checkpoint

3. **Добавление организма при продолжении** (`--resume` + `--inject`):
   - Загружается состояние из checkpoint
   - Инъецируется дополнительный организм
   - Удобно для экспериментов с несколькими видами

4. **Debug режим** (`--debug`):
   - Записывает каждый цикл: состояние организмов, память, события
   - Можно фильтровать по циклам (`--from`, `--to`)
   - `--summary` выводит только итоговую таблицу

---

## proteus checkpoint

Работа с checkpoint файлами (.mv).

### Подкоманды

#### checkpoint info

Показать информацию о checkpoint:

```bash
proteus checkpoint info FILE
```

Вывод:
- Версия, количество циклов, seed
- Размер soup, mutation rate
- Список организмов с именами
- Статистика: spawns, deaths
- Состояние RNG

#### checkpoint diff

Сравнить два checkpoint:

```bash
proteus checkpoint diff FILE1 FILE2
```

Показывает различия в:
- Количестве циклов
- Состоянии soup
- Организмах
- RNG state

### Примеры

```bash
# Информация о checkpoint
proteus checkpoint info experiment.mv

# Проверка детерминизма
proteus run --resume base.mv --cycles 1000 --save run1.mv
proteus run --resume base.mv --cycles 1000 --save run2.mv
proteus checkpoint diff run1.mv run2.mv  # должны совпасть!
```

---

## proteus assemble

Ассемблирование .asm файлов.

### Синтаксис

```bash
proteus assemble FILE [options]
```

### Опции

| Опция | Описание |
|-------|----------|
| `-d, --disassemble` | Показать дизассемблированный код |
| `-o, --output FILE` | Сохранить бинарный вывод |
| `-v, --verbose` | Подробный вывод |

### Примеры

```bash
# Проверить синтаксис
proteus assemble myorg.asm

# С дизассемблером
proteus assemble myorg.asm -d

# Сохранить бинарник
proteus assemble myorg.asm -o myorg.bin
```

---

## proteus analyze

Анализ сохранённых данных симуляции.

### Синтаксис

```bash
proteus analyze [options]
```

### Опции

| Опция | Описание |
|-------|----------|
| `-i, --input FILE` | Входной файл данных |
| `--genome-stats` | Статистика по геномам |
| `--population` | График популяции |

---

## proteus info

Информация о программе и конфигурации по умолчанию.

```bash
proteus info
```

Показывает:
- Версию
- Конфигурацию по умолчанию
- Quick start guide
- Список примеров организмов

---

## JVM и GC настройки

Для долгих симуляций важны правильные настройки JVM.

### Рекомендуемые GC

| JDK версия | Рекомендуемый GC | Опции |
|------------|------------------|-------|
| JDK 24+ | ZGC (generational по умолчанию) | `-XX:+UseZGC` |
| JDK 21-23 | ZGC Generational | `-XX:+UseZGC -XX:+ZGenerational` |
| JDK 17-20 | ZGC | `-XX:+UseZGC` |
| JDK 11-16 | G1GC | `-XX:+UseG1GC -XX:MaxGCPauseMillis=200` |

### Примеры запуска

```bash
# Короткая симуляция (до 1M циклов)
java -Xmx1g -jar proteus.jar run ...

# Средняя симуляция (1-10M циклов) — JDK 24+
java -Xmx2g -XX:+UseZGC -jar proteus.jar run ...

# Долгая симуляция (неделя+)
java -Xmx4g \
    -XX:+UseZGC \
    -Xlog:gc*:file=gc.log:time:filecount=3,filesize=10m \
    -jar proteus.jar run \
    --checkpoint-interval 100000 \
    ...
```

### Рекомендации по памяти

| Soup size | Max organisms | Рекомендуемый -Xmx |
|-----------|---------------|-------------------|
| 100K | 1000 | 512m |
| 1M | 1000 | 1g |
| 1M | 5000 | 2g |
| 1M | 10000 | 4g |
| 10M | 10000 | 8g |

### Поведение при нехватке памяти

При heap usage > 85%:
1. Сохраняется emergency checkpoint (если указан `--save`)
2. Вызывается `System.gc()`

При heap usage > 90%:
1. Симуляция ставится на паузу
2. Делается до 5 попыток GC с паузами 2-10 секунд
3. Если не помогло — выводится warning в лог

---

## Примеры использования

### Простой эксперимент

```bash
# Запуск на 100K циклов
proteus run --inject examples/ancestor.asm --cycles 100000

# С сохранением результата
proteus run --inject examples/ancestor.asm \
    --cycles 100000 --save result.mv

# Посмотреть что получилось
proteus checkpoint info result.mv
```

### Эксперимент с несколькими видами

```bash
# Шаг 1: Паразит
proteus run --inject examples/parasite.asm --name Para \
    --cycles 1 --seed 12345 -s 1000000 --save exp.mv

# Шаг 2: Хаотик
proteus run --inject examples/chaotic.asm --name Chao \
    --cycles 1 --resume exp.mv --save exp.mv

# Шаг 3: Ancestor
proteus run --inject examples/ancestor.asm --name Anc \
    --cycles 1 --resume exp.mv --save exp.mv

# Шаг 4: Долгая симуляция
proteus run --resume exp.mv \
    --cycles 1000000 --max-organisms 5000 --save final.mv

# Шаг 5: Анализ
proteus checkpoint info final.mv
```

### Воспроизводимость

```bash
# Запуск с seed
proteus run --inject ancestor.asm --cycles 10000 \
    --seed 42 --save run1.mv

# Повторить с тем же seed
proteus run --inject ancestor.asm --cycles 10000 \
    --seed 42 --save run2.mv

# Проверить идентичность
proteus checkpoint diff run1.mv run2.mv
# Output: "Checkpoints are identical"
```

### Debug workflow

```bash
# Записать первые 100 циклов
proteus run --inject ancestor.asm \
    --cycles 100 --debug initial.txt --save state.mv

# Долгая симуляция (без debug)
proteus run --resume state.mv \
    --cycles 500000 --save state.mv

# Посмотреть что происходит
proteus run --resume state.mv \
    --cycles 100 --debug after500k.txt

# Сравнить initial.txt и after500k.txt
```

---

## Формат checkpoint файлов

Checkpoint файлы (`.mv`) используют формат MVStore (H2 Database).

Содержимое:
- Метаданные (версия, циклы, seed)
- Конфигурация симуляции
- Состояние soup (память)
- Список организмов с их состоянием
- RNG state для детерминированного resume

**Версия формата:** 1

---

## См. также

- [README.md](../README.md) — обзор проекта
- [ISA_SPEC.md](ISA_SPEC.md) — спецификация инструкций
- [ASM_SPEC.md](ASM_SPEC.md) — синтаксис ассемблера
- [ARCHITECTURE.md](ARCHITECTURE.md) — архитектура системы
