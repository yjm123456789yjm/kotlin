/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#ifndef RUNTIME_METRIC_COLLECTOR_H
#define RUNTIME_METRIC_COLLECTOR_H

#include <string_view>

namespace kotlin {

void Post(std::string_view name, int64_t value) noexcept;

void ZoneStart(std::string_view name) noexcept;
void ZoneEnd(std::string_view name) noexcept;

void ThreadDone() noexcept;

void Flush() noexcept;

}  // namespace

#endif // RUNTIME_METRIC_COLLECTOR_H
