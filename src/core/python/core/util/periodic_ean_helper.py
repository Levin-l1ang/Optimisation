def transformTimeToPeriodic(time: int, period_length: int):
    return ((time % period_length) + period_length) % period_length
