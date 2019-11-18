package com.stfalcon.awarenessdemo

import com.google.android.gms.awareness.fence.AwarenessFence

class SelectedFenceParameters(
    val parametersNames: MutableList<String>,
    val fenceList: List<AwarenessFence>
)